/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.h2o.sparkling.ml.utils

import java.io.File

import hex.genmodel.{ModelMojoReader, MojoModel, MojoReaderBackendFactory}
import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.expressions.GenericRow

object Utils {
  def getMojoModel(mojoFile: File): MojoModel = {
    val reader = MojoReaderBackendFactory.createReaderBackend(mojoFile.getAbsolutePath)
    ModelMojoReader.readFrom(reader)
  }

  def arrayToRow(array: Array[Double]): Row = new GenericRow(array.map(_.asInstanceOf[Any]))

  def arrayToRow(array: Array[Float]): Row = new GenericRow(array.map(_.asInstanceOf[Any]))
}
