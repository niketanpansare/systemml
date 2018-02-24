/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sysml.runtime.instructions.gpu.context;

import static jcuda.runtime.JCuda.cudaFree;
import static jcuda.runtime.JCuda.cudaMalloc;
import static jcuda.runtime.JCuda.cudaMemGetInfo;
import static jcuda.runtime.JCuda.cudaMemset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.atomic.LongAdder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sysml.api.DMLScript;
import org.apache.sysml.conf.ConfigurationManager;
import org.apache.sysml.conf.DMLConfig;
import org.apache.sysml.hops.OptimizerUtils;
import org.apache.sysml.runtime.DMLRuntimeException;
import org.apache.sysml.runtime.instructions.gpu.GPUInstruction;
import org.apache.sysml.utils.GPUStatistics;

import jcuda.Pointer;

/**
 * - All cudaFree and cudaMalloc in SystemML should go through this class to avoid OOM or incorrect results.
 * - This class 
 */
public class GPUMemoryManager {
	protected static final Log LOG = LogFactory.getLog(GPUMemoryManager.class.getName());
	// Invoke cudaMemGetInfo to get available memory information. Useful if GPU is shared among multiple application.
	public double GPU_MEMORY_UTILIZATION_FACTOR = ConfigurationManager.getDMLConfig()
			.getDoubleValue(DMLConfig.GPU_MEMORY_UTILIZATION_FACTOR);
	
	/**
	 * Map of free blocks allocate on GPU. maps size_of_block -> pointer on GPU
	 */
	private HashMap<Long, ArrayList<Pointer>> rmvarGPUPointers = new HashMap<Long, ArrayList<Pointer>>();
	
	/**
	 * list of allocated {@link GPUObject} instances allocated on {@link GPUContext#deviceNum} GPU
	 * These are matrices allocated on the GPU on which rmvar hasn't been called yet.
	 * If a {@link GPUObject} has more than one lock on it, it cannot be freed
	 * If it has zero locks on it, it can be freed, but it is preferrable to keep it around
	 * so that an extraneous host to dev transfer can be avoided
	 */
	private ArrayList<GPUObject> allocatedGPUObjects = new ArrayList<>();
	
	/**
	 * To record size of allocated blocks
	 */
	private HashMap<Pointer, Long> allocatedGPUPointers = new HashMap<>();
	
	public GPUMemoryManager(GPUContext gpuCtx) {
		long free[] = { 0 };
		long total[] = { 0 };
		cudaMemGetInfo(free, total);
		LOG.info(" GPU memory - Total: " + (total[0] * (1e-6)) + " MB, Available: " + (free[0] * (1e-6)) + " MB on "
				+ gpuCtx);
		if (GPUContextPool.initialGPUMemBudget() > OptimizerUtils.getLocalMemBudget()) {
			LOG.warn("Potential under-utilization: GPU memory (" + GPUContextPool.initialGPUMemBudget()
					+ ") > driver memory budget (" + OptimizerUtils.getLocalMemBudget() + "). "
					+ "Consider increasing the driver memory budget.");
		}
	}
	
	/**
	 * Invoke cudaMalloc
	 * 
	 * @param A pointer
	 * @param size size in bytes
	 * @return allocated pointer
	 */
	private Pointer cudaMallocWarnIfFails(Pointer A, long size) {
		try {
			cudaMalloc(A, size);
			allocatedGPUPointers.put(A, size);
			return A;
		} catch(jcuda.CudaException e) {
			LOG.warn("cudaMalloc failed immediately after cudaMemGetInfo reported that memory of size " + size + " is available. "
					+ "This usually happens if there are external programs trying to grab on to memory in parallel.");
			return null;
		}
	}
	
	/**
	 * Allocate pointer of the given size in bytes.
	 * 
	 * @param opcode instruction name
	 * @param size size in bytes
	 * @return allocated pointer
	 * @throws DMLRuntimeException if error
	 */
	public Pointer malloc(String opcode, long size) throws DMLRuntimeException {
		if(size < 0) {
			throw new DMLRuntimeException("Cannot allocate memory of size " + size);
		}
		long t0 = DMLScript.STATISTICS ? System.nanoTime() : 0;
		// Step 1: First try reusing exact match in rmvarGPUPointers to avoid holes in the GPU memory
		Pointer A = getRmvarPointer(opcode, size);
		
		// Step 2: Allocate a new pointer in the GPU memory (since memory is available)
		if(A == null && size <= getAvailableMemory()) {
			A = cudaMallocWarnIfFails(new Pointer(), size);
			if(LOG.isTraceEnabled()) {
				if(A == null)
					LOG.trace("Couldnot allocate a new pointer in the GPU memory:" + size);
				else
					LOG.trace("Allocated a new pointer in the GPU memory:" + size);
			}
		}
		
		// Step 3: Try reusing non-exact match entry of rmvarGPUPointers
		if(A == null) { 
			// Find minimum key that is greater than size
			long key = Long.MAX_VALUE;
			for(Long k : rmvarGPUPointers.keySet()) {
				key = k > size ? Math.min(key, k) : key;
			}
			if(key != Long.MAX_VALUE) {
				A = getRmvarPointer(opcode, key);
				// To avoid potential for holes in the GPU memory
				guardedCudaFree(A);
				A = cudaMallocWarnIfFails(new Pointer(), size);
				if(LOG.isTraceEnabled()) {
					if(A == null)
						LOG.trace("Couldnot reuse non-exact match of rmvarGPUPointers:" + size);
					else
						LOG.trace("Reuses a non-exact match from rmvarGPUPointers:" + size);
				}
			}
		}
		
		// Step 4: Eagerly free-up rmvarGPUPointers and check if memory is available on GPU
		if(A == null) {
			for(ArrayList<Pointer> ptrs : rmvarGPUPointers.values()) {
				for(Pointer toFree : ptrs) {
					guardedCudaFree(toFree);
				}
			}
			if(size <= getAvailableMemory()) {
				A = cudaMallocWarnIfFails(new Pointer(), size);
				if(LOG.isTraceEnabled()) {
					if(A == null)
						LOG.trace("Couldnot allocate a new pointer in the GPU memory after eager free:" + size);
					else
						LOG.trace("Allocated a new pointer in the GPU memory after eager free:" + size);
				}
			}
		}
		
		addMiscTime(opcode, GPUStatistics.cudaAllocTime, GPUStatistics.cudaAllocCount, GPUInstruction.MISC_TIMER_ALLOCATE, t0);
		
		// Step 5: Try eviction based on the given policy
		if(A == null) {
			t0 = DMLScript.STATISTICS ? System.nanoTime() : 0;
			// Sort based on the eviction policy
			Collections.sort(allocatedGPUObjects, new GPUComparator(size));
			while (size > getAvailableMemory() && allocatedGPUObjects.size() > 0) {
				GPUObject toBeRemoved = allocatedGPUObjects.get(allocatedGPUObjects.size() - 1);
				if (toBeRemoved.isLocked()) {
					// All remaining blocks will also be locked
					break;
				}
				else {
					// Perform eviction
					if (toBeRemoved.dirty) {
						toBeRemoved.copyFromDeviceToHost(opcode, true);
					}
					toBeRemoved.clearData(true);
				}
			}
			addMiscTime(opcode, GPUStatistics.cudaEvictionCount, GPUStatistics.cudaEvictTime, GPUInstruction.MISC_TIMER_EVICT, t0);
			if(size <= getAvailableMemory()) {
				A = cudaMallocWarnIfFails(new Pointer(), size);
				if(LOG.isTraceEnabled()) {
					if(A == null)
						LOG.trace("Couldnot allocate a new pointer in the GPU memory after eviction:" + size);
					else
						LOG.trace("Allocated a new pointer in the GPU memory after eviction:" + size);
				}
			}
		}
		
		if(A == null) {
			throw new DMLRuntimeException("There is not enough memory on device for this matrix, request (" + size + "). "
					+ toString());
		}
		
		t0 = DMLScript.STATISTICS ? System.nanoTime() : 0;
		cudaMemset(A, 0, size);
		addMiscTime(opcode, GPUStatistics.cudaMemSet0Time, GPUStatistics.cudaMemSet0Count, GPUInstruction.MISC_TIMER_SET_ZERO, t0);
		return A;
	}
	
	private void guardedCudaFree(Pointer toFree) {
		guardedCudaFree(toFree, true);
	}
	
	private void guardedCudaFree(Pointer toFree, boolean removeFromAllocatedGPUPointers) {
		if (toFree != new Pointer()) {
			cudaFree(toFree);
			if(removeFromAllocatedGPUPointers)
				allocatedGPUPointers.remove(toFree);
		}
	}
	
	/**
	 * Deallocate the pointer
	 * 
	 * @param opcode instruction name
	 * @param toFree pointer to free
	 * @param eager whether to deallocate eagerly
	 * @throws DMLRuntimeException if error
	 */
	public void free(String opcode, Pointer toFree, boolean eager) throws DMLRuntimeException {
		Pointer dummy = new Pointer();
		if (toFree == dummy) { // trying to free a null pointer
			return;
		}
		if (eager) {
			long t0 = DMLScript.STATISTICS ? System.nanoTime() : 0;
			guardedCudaFree(toFree);
			addMiscTime(opcode, GPUStatistics.cudaDeAllocTime, GPUStatistics.cudaDeAllocCount, GPUInstruction.MISC_TIMER_CUDA_FREE, t0);
		}
		else {
			if (!allocatedGPUPointers.containsKey(toFree))
				throw new RuntimeException("ERROR : Internal state corrupted, cache block size map is not aware of a block it trying to free up");
			long size = allocatedGPUPointers.get(toFree);
			ArrayList<Pointer> freeList = rmvarGPUPointers.get(size);
			if (freeList == null) {
				freeList = new ArrayList<Pointer>();
				rmvarGPUPointers.put(size, freeList);
			}
			if (freeList.contains(toFree))
				throw new RuntimeException("GPU : Internal state corrupted, double free");
			freeList.add(toFree);
		}
	}
	
	/**
	 * Clear the allocated GPU objects
	 * 
	 * @throws DMLRuntimeException if error
	 */
	public synchronized void clearMemory() throws DMLRuntimeException {
		clearTemporaryMemory();
		while (!allocatedGPUObjects.isEmpty()) {
			GPUObject o = allocatedGPUObjects.get(0);
			if (o.isDirty()) {
				LOG.warn("Attempted to free GPU Memory when a block[" + o + "] is still on GPU memory, copying it back to host.");
				o.acquireHostRead(null);
			}
			o.clearData(true);
		}
		allocatedGPUObjects.clear();
	}
	
	/**
	 * Clears up the memory used to optimize cudaMalloc/cudaFree calls.
	 */
	public void clearTemporaryMemory() {
		// To record the cuda block sizes needed by allocatedGPUObjects, others are cleared up.
		HashMap<Pointer, Long> tmpallocatedGPUPointers = new HashMap<>();
		for (GPUObject o : allocatedGPUObjects) {
			if (o.isDirty()) {
				if (o.isSparse()) {
					CSRPointer p = o.getSparseMatrixCudaPointer();
					if (p == null)
						throw new RuntimeException("CSRPointer is null in clearTemporaryMemory");
					if (p.rowPtr != null && allocatedGPUPointers.containsKey(p.rowPtr)) {
						tmpallocatedGPUPointers.put(p.rowPtr, allocatedGPUPointers.get(p.rowPtr));
					}
					if (p.colInd != null && allocatedGPUPointers.containsKey(p.colInd)) {
						tmpallocatedGPUPointers.put(p.colInd, allocatedGPUPointers.get(p.colInd));
					}
					if (p.val != null && allocatedGPUPointers.containsKey(p.val)) {
						tmpallocatedGPUPointers.put(p.val, allocatedGPUPointers.get(p.val));
					}

				} else {
					Pointer p = o.getJcudaDenseMatrixPtr();
					if (p == null)
						throw new RuntimeException("Pointer is null in clearTemporaryMemory");
					tmpallocatedGPUPointers.put(p, allocatedGPUPointers.get(p));
				}
			}
		}

		// garbage collect all temporarily allocated spaces
		for (ArrayList<Pointer> l : rmvarGPUPointers.values()) {
			for (Pointer p : l) {
				guardedCudaFree(p);
			}
		}
		allocatedGPUPointers.clear();
		rmvarGPUPointers.clear();

		// Restore only those entries for which there are still blocks on the GPU
		allocatedGPUPointers.putAll(tmpallocatedGPUPointers);
	}
	
	private void addMiscTime(String opcode, LongAdder globalGPUTimer, LongAdder globalGPUCounter, String instructionLevelTimer, long startTime) {
		if(DMLScript.STATISTICS) {
			long totalTime = System.nanoTime() - startTime;
			globalGPUTimer.add(totalTime);
			globalGPUCounter.add(1);
			if (opcode != null && DMLScript.FINEGRAINED_STATISTICS)
				GPUStatistics.maintainCPMiscTimes(opcode, instructionLevelTimer, totalTime);
		}
	}
	
	private void addMiscTime(String opcode, String instructionLevelTimer, long startTime) {
		if (opcode != null && DMLScript.FINEGRAINED_STATISTICS)
			GPUStatistics.maintainCPMiscTimes(opcode, instructionLevelTimer, System.nanoTime() - startTime);
	}
	
	private Pointer getRmvarPointer(String opcode, long size) {
		if (rmvarGPUPointers.containsKey(size)) {
			if(LOG.isTraceEnabled())
				LOG.trace("Getting rmvar-ed pointers for size:" + size);
			long t0 = opcode != null && DMLScript.FINEGRAINED_STATISTICS ?  System.nanoTime() : 0;
			Pointer A = rmvarGPUPointers.get(size).remove(0);
			if (rmvarGPUPointers.get(size).isEmpty())
				rmvarGPUPointers.remove(size);
			addMiscTime(opcode, GPUInstruction.MISC_TIMER_REUSE, t0);
			return A;
		}
		else {
			return null;
		}
	}
	
	
	/**
	 * Print debug information
	 */
	public String toString() {
		long sizeOfLockedGPUObjects = 0; long numLockedGPUObjects = 0;
		long sizeOfUnlockedGPUObjects = 0; long numUnlockedGPUObjects = 0;
		for(GPUObject gpuObj : allocatedGPUObjects) {
			try {
				if(gpuObj.isLocked()) {
					numLockedGPUObjects++;
					sizeOfLockedGPUObjects += gpuObj.getSizeOnDevice();
				}
				else {
					numUnlockedGPUObjects++;
					sizeOfUnlockedGPUObjects += gpuObj.getSizeOnDevice();
				}
			} catch (DMLRuntimeException e) {
				throw new RuntimeException(e);
			}
		}
		long totalMemoryAllocated = 0;
		for(Long numBytes : allocatedGPUPointers.values()) {
			totalMemoryAllocated += numBytes;
		}
		return "Num of GPU objects: [unlocked:" + numUnlockedGPUObjects + ", locked:" + numLockedGPUObjects + "]. "
				+ "Size of GPU objects in bytes: [unlocked:" + sizeOfUnlockedGPUObjects + ", locked:" + sizeOfLockedGPUObjects + "]. "
				+ "Total memory allocated by the current GPU context in bytes:" + totalMemoryAllocated;
	}
	
	/**
	 * Gets the available memory on GPU that SystemML can use.
	 *
	 * @return the available memory in bytes
	 */
	public long getAvailableMemory() {
		long free[] = { 0 };
		long total[] = { 0 };
		cudaMemGetInfo(free, total);
		return (long) (free[0] * GPU_MEMORY_UTILIZATION_FACTOR);
	}
	
	public static class GPUComparator implements Comparator<GPUObject> {
		private long neededSize;
		public GPUComparator(long neededSize) {
			this.neededSize = neededSize;
		}
		@Override
		public int compare(GPUObject p1, GPUObject p2) {
			if (p1.isLocked() && p2.isLocked()) {
				// Both are locked, so don't sort
				return 0;
			} else if (p1.isLocked()) {
				// Put the unlocked one to RHS
				// a value less than 0 if x < y; and a value greater than 0 if x > y
				return -1;
			} else if (p2.isLocked()) {
				// Put the unlocked one to RHS
				// a value less than 0 if x < y; and a value greater than 0 if x > y
				return 1;
			} else {
				// Both are unlocked
				if (DMLScript.GPU_EVICTION_POLICY == DMLScript.EvictionPolicy.MIN_EVICT) {
					long p1Size = 0;
					long p2Size = 0;
					try {
						p1Size = p1.getSizeOnDevice() - neededSize;
						p2Size = p2.getSizeOnDevice() - neededSize;
					} catch (DMLRuntimeException e) {
						throw new RuntimeException(e);
					}

					if (p1Size >= 0 && p2Size >= 0) {
						return Long.compare(p2Size, p1Size);
					} else {
						return Long.compare(p1Size, p2Size);
					}
				} else {
					return Long.compare(p2.timestamp.get(), p1.timestamp.get());
				}
			}
		}
	}
}
