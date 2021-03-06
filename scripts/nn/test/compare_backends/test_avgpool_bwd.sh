#!/usr/bin/bash
#-------------------------------------------------------------
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
#-------------------------------------------------------------

jars='systemml-*-extra.jar'

# N = Number of images, C = number of channels, H = height, W = width
N=5
C=3
H=28
W=28
for sparsity in 0.1 0.2 0.5 0.6 0.9
do
	# Generating the data

	for stride in 1 2 3
	do
		for pad in 0 1 2
		do
			$SPARK_HOME/bin/spark-submit SystemML.jar -f gen_maxpool_bwd.dml -nvargs sp=$sparsity N=$N C=$C H=$H W=$W pool=3 stride=$stride pad=$pad
			# Running a test in CPU mode
			$SPARK_HOME/bin/spark-submit SystemML.jar -f test_avgpool_bwd.dml -nvargs stride=$stride pad=$pad out=out_cp.csv N=$N C=$C H=$H W=$W pool=3
			# Running a test in GPU mode
			$SPARK_HOME/bin/spark-submit --jars $jars SystemML.jar -f test_avgpool_bwd.dml -stats -gpu force -nvargs stride=$stride pad=$pad out=out_gpu.csv N=$N C=$C H=$H W=$W pool=3
			# Comparing the CPU vs GPU results to make sure they are the same
			$SPARK_HOME/bin/spark-submit SystemML.jar -f compare.dml -args out_cp.csv out_gpu.csv "avgpool_bwd:sparsity="$sparsity",stride="$stride",pad="$pad
			rm -rf out_cp.csv out_gpu.csv out_cp.csv.mtd out_gpu.csv.mtd
		done
	done
	rm -rf input.mtx input.mtx.mtd
done
