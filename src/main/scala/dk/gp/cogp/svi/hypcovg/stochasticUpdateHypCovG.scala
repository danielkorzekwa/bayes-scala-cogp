package dk.gp.cogp.svi.hypcovg

import breeze.linalg.DenseVector
import dk.gp.cogp.CogpModel
import breeze.linalg.DenseMatrix
import dk.gp.cogp.svi.classicalMomentum
import breeze.generic.UFunc
import dk.gp.cogp.lb.LowerBound

object stochasticUpdateHypCovG {

  private val learningRate = 1e-5
  private val momentum = 0.9

  def apply(j: Int, lowerBound:LowerBound,model: CogpModel, x: DenseMatrix[Double], y: DenseMatrix[Double]): (DenseVector[Double], DenseVector[Double]) = {

    val hypParamsD = calcLBGradHypCovG(j, lowerBound,model, x, y)

    val (newHypParams, newHypParamsDelta) = classicalMomentum(model.g(j).covFuncParams, model.g(j).covFuncParamsDelta, learningRate, momentum, hypParamsD)

    (newHypParams, newHypParamsDelta)
  }

}