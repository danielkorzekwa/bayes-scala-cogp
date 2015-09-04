package dk.gp.cogp.svi.hypcovh

import dk.gp.cogp.CogpModel
import breeze.linalg.DenseMatrix
import breeze.linalg.DenseVector
import dk.gp.cogp.svi.classicalMomentum
import dk.gp.cogp.lb.LowerBound

object stochasticUpdateHypCovH {
  
  private val learningRate = 1e-5
  private val momentum = 0.9
  
  def apply(i: Int, lowerBound: LowerBound,model: CogpModel, x: DenseMatrix[Double], y: DenseMatrix[Double]):(DenseVector[Double], DenseVector[Double]) = {
  val hypParamsD = calcLBGradHypCovH(i, lowerBound,model, x, y)

    val (newHypParams, newBetaDelta) = classicalMomentum(model.h(i).covFuncParams, model.h(i).covFuncParamsDelta, learningRate, momentum, hypParamsD)

    (newHypParams, newBetaDelta)
  }
}