package dk.gp.hgpc

import breeze.linalg._
import breeze.linalg.DenseMatrix
import breeze.linalg.DenseVector
import dk.bayes.dsl.factor._
import dk.bayes.math.gaussian.canonical.CanonicalGaussian
import dk.bayes.math.gaussian.canonical.DenseCanonicalGaussian
import dk.gp.gp.gpPredictSingle
import dk.gp.gpc.util.calcLoglikGivenLatentVar
import dk.gp.hgpc.util.TaskVariable
import dk.gp.math.MultivariateGaussian
import dk.gp.gpc.util.createLikelihoodVariables
import dk.gp.hgpc.util.createHgpcFactorGraph
import com.typesafe.scalalogging.slf4j.LazyLogging
import dk.bayes.dsl.epnaivebayes.EPNaiveBayesFactorGraph

/**
 * Hierarchical Gaussian Process classification. Multiple Gaussian Processes for n tasks with a single shared parent GP.
 */
object hgpcPredict extends LazyLogging {

  case class TaskPosterior(x: DenseMatrix[Double], xPosterior: DenseCanonicalGaussian)

  /**
   * Returns vector of probabilities for a class 1
   */
  def apply(t: DenseMatrix[Double], model: HgpcModel): DenseVector[Double] = {
    val taskPosteriorByTaskId: Map[Int, TaskPosterior] = createTaskPosteriorByTaskId(t, model)

    val predictedArray = (0 until t.rows).par.map { rowIndex =>

      val tRow = t(rowIndex, ::).t
      val taskId = tRow(0).toInt
      val taskPosterior = taskPosteriorByTaskId(taskId)

      val tTestPrior = gpPredictSingle(tRow.toDenseMatrix, MultivariateGaussian(taskPosterior.xPosterior.mean, taskPosterior.xPosterior.variance), taskPosterior.x, model.covFunc, model.covFuncParams, model.mean)
      val predictedProb = calcLoglikGivenLatentVar(tTestPrior.m(0), tTestPrior.v(0, 0), 1d)

      predictedProb
    }.toArray
    DenseVector(predictedArray)
  }

  private def createTaskPosteriorByTaskId(xTest: DenseMatrix[Double], model: HgpcModel): Map[Int, TaskPosterior] = {

    val now = System.currentTimeMillis()
    logger.info("Calibrating factor graph...")
    val hgpcFactorGraph = createHgpcFactorGraph(model)
    val iterations = hgpcFactorGraph.calibrate(maxIter = 10, threshold = 1e-3)
    if (iterations >= 10) logger.warn(s"Factor graph did not converge in less than 10 iterations")
    logger.info("Calibrating factor graph...done: " + (System.currentTimeMillis() - now))

    val uPosterior = hgpcFactorGraph.getPosterior().asInstanceOf[DenseCanonicalGaussian]

    val testTaskIds = xTest(::, 0).toArray.distinct
    val taskPosteriorByTaskId: Map[Int, TaskPosterior] = testTaskIds.map { taskId =>
      val idx = model.x(::, 0).findAll { x => x == taskId }
      val taskX = model.x(idx, ::).toDenseMatrix
      val taskY = model.y(idx).toDenseVector

      val taskPosterior = if (taskY.size == 0) TaskPosterior(model.u, uPosterior)
      else {

        val taskXTestIdx = xTest(::, 0).findAll(x => x == taskId)
        val taskXTest = xTest(taskXTestIdx, ::).toDenseMatrix

        val taskXX = DenseMatrix.vertcat(taskX, taskXTest)
        val xPrior = gpPredictSingle(taskXX, MultivariateGaussian(uPosterior.mean, uPosterior.variance), model.u, model.covFunc, model.covFuncParams)
        val xPriorVariable = dk.bayes.dsl.variable.gaussian.multivariate.MultivariateGaussian(xPrior.m, xPrior.v)
        val yVariables = createLikelihoodVariables(xPriorVariable, taskY)

        val factorGraph = EPNaiveBayesFactorGraph(xPriorVariable, yVariables, true)
        val iterations = factorGraph.calibrate(maxIter = 10, threshold = 1e-4)
        if (iterations >= 10) logger.warn(s"Factor graph did not converge in less than 10 iterations")
        val xPosteriorVariable = factorGraph.getPosterior().asInstanceOf[DenseCanonicalGaussian]

        TaskPosterior(taskXX, DenseCanonicalGaussian(xPosteriorVariable.mean, xPosteriorVariable.variance))
      }

      taskId.toInt -> taskPosterior
    }.toList.toMap

    taskPosteriorByTaskId
  }

}