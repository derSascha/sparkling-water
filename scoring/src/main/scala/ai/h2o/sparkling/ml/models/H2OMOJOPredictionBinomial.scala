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
package ai.h2o.sparkling.ml.models

import ai.h2o.sparkling.ml.utils.Utils
import hex.genmodel.easy.EasyPredictModelWrapper
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions.col
import ai.h2o.sparkling.sql.functions.udf
import org.apache.spark.sql.types._
import org.apache.spark.sql.{Column, Row}

import scala.collection.mutable

trait H2OMOJOPredictionBinomial extends PredictionWithContributions {
  self: H2OMOJOModel =>

  private def supportsCalibratedProbabilities(predictWrapper: EasyPredictModelWrapper): Boolean = {
    // calibrateClassProbabilities returns false if model does not support calibrated probabilities,
    // however it also accepts array of probabilities to calibrate. We are not interested in calibration,
    // but to call this method, we need to pass dummy array of size 2 with default values to 0.
    predictWrapper.m.calibrateClassProbabilities(Array.fill[Double](3)(0))
  }

  def getBinomialPredictionUDF(): UserDefinedFunction = {
    val schema = getBinomialPredictionSchema()
    val function = (r: Row, offset: Double) => {
      val model = H2OMOJOCache.getMojoBackend(uid, getMojo, this)
      val resultBuilder = mutable.ArrayBuffer[Any]()
      val pred = model.predictBinomial(RowConverter.toH2ORowData(r), offset)
      resultBuilder += pred.label
      if (getWithDetailedPredictionCol()) {
        resultBuilder += Utils.arrayToRow(pred.classProbabilities)
        if (getWithContributions()) {
          resultBuilder += Utils.arrayToRow(pred.contributions)
        }
        if (supportsCalibratedProbabilities(model)) {
          resultBuilder += Utils.arrayToRow(pred.calibratedClassProbabilities)
        }
        if (getWithLeafNodeAssignments()) {
          resultBuilder += pred.leafNodeAssignments
        }
      }
      new GenericRowWithSchema(resultBuilder.toArray, schema)
    }
    udf(function, schema)
  }

  private val predictionColType = StringType
  private val predictionColNullable = true

  def getBinomialPredictionColSchema(): Seq[StructField] = {
    Seq(StructField(getPredictionCol(), predictionColType, nullable = predictionColNullable))
  }

  def getBinomialPredictionSchema(): StructType = {
    val labelField = StructField("label", predictionColType, nullable = predictionColNullable)
    val baseFields = labelField :: Nil

    val fields = if (getWithDetailedPredictionCol()) {
      val model = H2OMOJOCache.getMojoBackend(uid, getMojo, this)
      val classFields = model.getResponseDomainValues.map(StructField(_, DoubleType, nullable = false))
      val probabilitiesField = StructField("probabilities", StructType(classFields), nullable = false)
      val detailedPredictionFields = baseFields :+ probabilitiesField

      val contributionsFields = if (getWithContributions()) {
        val contributionsField = StructField("contributions", getContributionsSchema(model), nullable = false)
        detailedPredictionFields :+ contributionsField
      } else {
        detailedPredictionFields
      }

      val assignmentFields = if (getWithLeafNodeAssignments()) {
        val assignmentField =
          StructField("leafNodeAssignments", ArrayType(StringType, containsNull = false), nullable = false)
        contributionsFields :+ assignmentField
      } else {
        contributionsFields
      }

      if (supportsCalibratedProbabilities(H2OMOJOCache.getMojoBackend(uid, getMojo, this))) {
        val calibratedProbabilitiesField =
          StructField("calibratedProbabilities", StructType(classFields), nullable = false)
        assignmentFields :+ calibratedProbabilitiesField
      } else {
        assignmentFields
      }
    } else {
      baseFields
    }

    StructType(fields)
  }

  def extractBinomialPredictionColContent(): Column = {
    col(s"${getDetailedPredictionCol()}.label")
  }
}
