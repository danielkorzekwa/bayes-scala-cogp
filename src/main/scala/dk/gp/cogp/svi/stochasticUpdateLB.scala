package dk.gp.cogp.svi

import breeze.linalg.DenseMatrix
import dk.gp.cogp.svi.v.stochasticUpdateV
import dk.gp.cogp.svi.u.stochasticUpdateU
import dk.gp.cov.CovFunc
import dk.gp.cogp.svi.w.stochasticUpdateW
import dk.gp.cogp.svi.beta.stochasticUpdateBeta
import breeze.linalg.DenseVector
import dk.gp.cov.utils.covDiag
import dk.gp.cogp.CogpModel
import dk.gp.cogp.svi.hypcovg.stochasticUpdateHypCovG
import dk.gp.cogp.svi.hypcovh.stochasticUpdateHypCovH
import dk.gp.math.MultivariateGaussian
import dk.gp.cogp.lb.LowerBound

object stochasticUpdateLB {

  def apply(model: CogpModel, x: DenseMatrix[Double], y: DenseMatrix[Double]): CogpModel = {

    var currModel = model

    //@TODO when learning just the covParameters, at some iteration, loglik accuracy suddenly goes down, numerical stability issues? 
    //@TODO Given just gU and hypG are learned, learning first hypG then gU doesn't not converge (loglik is decreasing), why is that?
    val newU = (0 until model.g.size).map { j => stochasticUpdateU(j, LowerBound(currModel, x), currModel, x, y) }.toArray

    currModel = withNewGu(newU, currModel)

    val lowerBound = LowerBound(currModel, x)

    val (newW, newWDelta) = stochasticUpdateW(lowerBound, y)
    val (newBeta, newBetaDelta) = stochasticUpdateBeta(lowerBound, y)
    val newHypCovG: Array[(DenseVector[Double], DenseVector[Double])] = (0 until model.g.size).map { j => stochasticUpdateHypCovG(j, lowerBound, y) }.toArray

    currModel = withNewCovParamsG(newHypCovG, currModel)
    currModel = currModel.copy(w = newW, wDelta = newWDelta)

    currModel = currModel.copy(beta = newBeta, betaDelta = newBetaDelta)

    val newV = (0 until model.h.size).map { i => stochasticUpdateV(i, LowerBound(currModel, x), currModel, x, y) }.toArray
    currModel = withNewHu(newV, currModel)

    val newHypCovH: Array[(DenseVector[Double], DenseVector[Double])] = (0 until model.h.size).map { i => stochasticUpdateHypCovH(i, LowerBound(currModel, x), y) }.toArray
    currModel = withNewCovParamsH(newHypCovH, currModel)

    currModel
  }

  private def withNewGu(newGu: Array[MultivariateGaussian], model: CogpModel): CogpModel = {
    val newG = (0 until model.g.size).map { j =>
      model.g(j).copy(u = newGu(j))
    }.toArray
    val newModel = model.copy(g = newG)

    newModel
  }

  private def withNewCovParamsG(newHypCovG: Array[(DenseVector[Double], DenseVector[Double])], model: CogpModel): CogpModel = {
    val newG = (0 until model.g.size).map { j =>
      model.g(j).copy(covFuncParams = newHypCovG(j)._1, covFuncParamsDelta = newHypCovG(j)._2)
    }.toArray
    val newModel = model.copy(g = newG)

    newModel
  }

  private def withNewHu(newHu: Array[MultivariateGaussian], model: CogpModel): CogpModel = {
    val newH = (0 until model.h.size).map { i =>
      model.h(i).copy(u = newHu(i))
    }.toArray
    val newModel = model.copy(h = newH)

    newModel
  }

  private def withNewCovParamsH(newHypCovH: Array[(DenseVector[Double], DenseVector[Double])], model: CogpModel): CogpModel = {
    val newH = (0 until model.h.size).map { i =>
      model.h(i).copy(covFuncParams = newHypCovH(i)._1, covFuncParamsDelta = newHypCovH(i)._2)
    }.toArray
    val newModel = model.copy(h = newH)

    newModel
  }
}