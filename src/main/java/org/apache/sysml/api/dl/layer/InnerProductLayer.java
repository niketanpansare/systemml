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
package org.apache.sysml.api.dl.layer;

import java.util.ArrayList;

import org.apache.sysml.api.dl.Barista;
import org.apache.sysml.api.dl.utils.FillerUtils;
import org.apache.sysml.runtime.DMLRuntimeException;

import caffe.Caffe.InnerProductParameter;
import caffe.Caffe.LayerParameter;

public class InnerProductLayer extends Layer {

	InnerProductParameter innerParam;
	
	public InnerProductLayer(LayerParameter param) {
		super(param, "H_");
		weightVar = "w_" + layerID;
		innerParam = param.getInnerProductParam();
		if(innerParam.hasBiasFiller()) {
			biasVar = "bias_" + layerID;
		}
	}

	@Override
	public String getSetupDML() throws DMLRuntimeException {
		checkInput();
		
		ArrayList<String> shape = new ArrayList<String>();
		shape.add("" + innerParam.getNumOutput());
		shape.add(getBottomLayerOutputShape(1));
		shape.add(getBottomLayerOutputShape(2));
		shape.add(getBottomLayerOutputShape(3));
		String weights = FillerUtils.getFiller(weightVar, shape, innerParam.getWeightFiller(), 0);
		if(innerParam.hasBiasFiller()) {
			ArrayList<String> bias_shape = new ArrayList<String>();
			bias_shape.add("" + innerParam.getNumOutput());
			bias_shape.add("1");
			// Transposing to produce Prithvi's script
			weights += FillerUtils.getFiller(biasVar, bias_shape, innerParam.getBiasFiller(), 0)
					+ weightVar + " = t(" + weightVar + ");\n"
					+ biasVar + " = t(" + biasVar + ");\n";
			
		}
		else {
			weights += weightVar + " = t(" + weightVar + ");\n";
		}
		
		if(Barista.useMomentum) {
			return weights + updatePrefix + weightVar + " = matrix(0, rows=nrow(" + weightVar + "), cols=ncol(" + weightVar + "));\n" 
					+ updatePrefix + biasVar + " = matrix(0, rows=nrow(" + biasVar + "), cols=ncol(" + biasVar + "));\n";
		}
		else {
			return weights;
		}
	}

	@Override
	public String getForwardDML() throws DMLRuntimeException {
		String ret = outputVar + " = " + bottom.get(0).outputVar + " %*% " + weightVar;
		if(innerParam.hasBiasFiller()) {
			ret += " + " + biasVar;
		}
		return ret;
	}

	@Override
	public String getBackwardDML() throws DMLRuntimeException {
		String ret = gradientPrefix + weightVar + " = t(" + bottom.get(0).outputVar + ") %*% " + deltaVar + ";\n";
		if(innerParam.hasBiasFiller())
			ret += "\t" + gradientPrefix + biasVar  + " = colSums(" + deltaVar + ");\n";
		return ret + "\t" + bottom.get(0).deltaVar + " = " + deltaVar + " %*% t(" + weightVar + ");";
	}

	@Override
	public String getFinalizeDML() throws DMLRuntimeException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void updateOutputShape() throws DMLRuntimeException {
		output_shape.clear();
		output_shape.add(getBottomLayerOutputShape(0));
		output_shape.add("" + innerParam.getNumOutput());
		output_shape.add("1");
		output_shape.add("1");
	}

}