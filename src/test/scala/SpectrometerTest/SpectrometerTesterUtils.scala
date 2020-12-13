// SPDX-License-Identifier: Apache-2.0

package spectrometer

import breeze.math.Complex
import breeze.signal.{fourierTr, iFourierTr}
import breeze.linalg._
import breeze.plot._
import chisel3.util.log2Up


object SpectrometerTesterUtils {
    
  /**
  * Convert int data to binary string
  */
  def asNdigitBinary (source: Int, digits: Int): String = {
    val lstring = source.toBinaryString
    //val sign = if (source > 0) "%0" else "%1"
    if (source >= 0) {
      //val l: java.lang.Long = lstring.toLong
      val l: java.lang.Long = lstring.toLong
      String.format ("%0" + digits + "d", l)
    }
    else
      lstring.takeRight(digits)
  }
  
  
  /**
  * Generate real sinusoid. Assumption is that dataWidth of the fft input is always equal to 16
  * Scale parameter is useful when square magnitude is calculated to prevent overflow
  */

  def getTone(numSamples: Int, f1r: Double, scale: Int = 1): Seq[Int] = {
    (0 until numSamples).map(i => (math.sin(2 * math.Pi * f1r * i) * scala.math.pow(2, 14)/scale).toInt)
  }
  
  /**
  * Generate complex sinusoid. Assumption is that dataWidth of the fft input is always equal to 16
  * Scale parameter is useful when square magnitude is calculated to prevent overflow
  */
  
  def getComplexTone(numSamples: Int, f1r: Double, scale: Int = 1): Seq[Complex] = {
    (0 until numSamples).map(i => Complex((math.cos(2 * math.Pi * f1r * i) * scala.math.pow(2, 13)/scale).toInt, (math.sin(2 * math.Pi * f1r * i)*scala.math.pow(2, 13)/scale).toInt))
  }
  
  /**
  * Generate random signal. Assumption is that dataWidth of the fft input is always equal to 16
  * Scale parameter is useful when square magnitude is calculated to prevent overflow
  */
  def genRandSignal(numSamples: Int, scale: Int = 1, binPoint: Int = 14): Seq[Int] = {
    import scala.math.sqrt
    import scala.util.Random
    
    Random.setSeed(11110L)
    (0 until numSamples).map(x => (Random.nextDouble()*scala.math.pow(2, binPoint)/scale).toInt)
  }
  
  /**
  * Generate complex random signal. Assumption is that dataWidth of the fft input is always equal to 16
  * Scale parameter is useful when square magnitude is calculated to prevent overflow
  */
  def genComplexRandSignal(numSamples: Int, scale: Int = 1, binPoint: Int = 13): Seq[Complex] = {
    import scala.math.sqrt
    import scala.util.Random
    
    Random.setSeed(11110L)
    (0 until numSamples).map(x => Complex((Random.nextDouble()*scala.math.pow(2, binPoint)/scale).toInt, Random.nextDouble()*scala.math.pow(2, binPoint)/scale.toInt))
  }
  
  /**
  * Format inData so that it is compatible with 32 AXI4Stream data
  */
  def formAXI4StreamRealData(inData: Seq[Int], dataWidth: Int): Seq[Int] = {
    inData.map(data => java.lang.Long.parseLong(
                                  asNdigitBinary(data, dataWidth) ++ 
                                  asNdigitBinary(0, dataWidth), 2).toInt)
  }
  
  /**
  * Format complex inData so that it is compatible with 32 AXI4Stream data
  */
  def formAXI4StreamComplexData(inData : Seq[Complex], dataWidth: Int): Seq[Int] = {
    inData.map(data => java.lang.Long.parseLong(
                                  asNdigitBinary(data.real.toInt, dataWidth) ++ 
                                  asNdigitBinary(data.imag.toInt, dataWidth)).toInt)
  }
  
  
  /*****************************************************
  * Does bit reversal of the input data
  */
 
  def bit_reverse(in: Int, width: Int): Int = {
    import scala.math.pow
    var test = in
    var out = 0
    for (i <- 0 until width) {
      if (test / pow(2, width-i-1) >= 1) {
        out += pow(2,i).toInt
        test -= pow(2,width-i-1).toInt
      }
    }
    out
  }
  
  def bitrevorder_data(testSignal: Seq[Int]): Seq[Int] = {
    val seqLength = testSignal.size
    val new_indices = (0 until seqLength).map(x => bit_reverse(x, log2Up(seqLength)))
    new_indices.map(x => testSignal(x))
  }

 /*******************************************************/
  def log2(x: Double): Double =  scala.math.log(x)/scala.math.log(2)

  /****************************************************************
  * Plot functions
  */
  def plot_data(inputData: Seq[Int], plotName: String, fileName: String): Unit = {

    val f = Figure()
    val p = f.subplot(0)
    p.legend_=(true)
  
    val data = inputData.map(e => e.toDouble).toSeq
    val xaxis = (0 until data.length).map(e => e.toDouble).toSeq.toArray
    
    p += plot(xaxis, data.toArray, name = plotName)
   
    p.ylim(data.min, data.max)
    p.title_=(plotName + s" ${inputData.length}")

    p.xlabel = "Time Bins"
    p.ylabel = "Amplitude"
    f.saveas(s"test_run_dir/" + fileName)
  }
  
  def plot_fft(inputData: Seq[Long], plotName: String, fileName: String): Unit = {

    val f = Figure()
    val p = f.subplot(0)
    p.legend_=(true)
    
    val data = inputData.map(e => e.toDouble).toSeq
    val xaxis = (0 until data.length).map(e => e.toDouble).toSeq.toArray
    
    p += plot(xaxis, data.toArray, name = plotName)
   
    p.ylim(data.min, data.max)
    p.title_=(plotName + s" ${inputData.length}")

    p.xlabel = "Frequency Bin"
    p.ylabel = "Amplitude"
    f.saveas(s"test_run_dir/" + fileName)
  }
  /****************************************************************/
  
  /**
  * Calculate expected sine and cosine at the output of the nco module
  */
  def calcExpectedNcoOut(fftSize: Int, binWithPeak: Int): Seq[Complex] = {
    require(binWithPeak < fftSize, "Index of expected peak can not be larger than fft size")
    
    val genSinGolden = (1 until (fftSize + 1)).map(i => (math.sin(2 * math.Pi * binWithPeak/fftSize * i) * scala.math.pow(2, 14)).toInt)
    val genCosGolden = (1 until (fftSize + 1)).map(i => (math.cos(2 * math.Pi * binWithPeak/fftSize * i) * scala.math.pow(2, 14)).toInt)
    val genComplex = genSinGolden.zip(genCosGolden).map { case (sin, cos) => Complex(cos, sin) }
    genComplex
  }
  
  /**
  * Calculate expected output of the fft module
  */
  def calcExpectedFFTOut(fftSize: Int, binWithPeak: Int, scale: Int = 1): Seq[Complex] = {
    require(binWithPeak < fftSize, "Index of expected peak can not be larger than fft size")

    val ncoOut = calcExpectedNcoOut(fftSize, binWithPeak)
    val fftOut = fourierTr(DenseVector(ncoOut.map( c => Complex(c.real.toDouble/scale, c.imag.toDouble/scale)).toArray)).toScalaVector
    fftOut
  }
  
  /**
  * Calculate magnitude of the fft -> TODO:Replace it with JPL mag calculation
  */
  def calcExpectedMagOut(fftSize: Int, binWithPeak: Int, scale: Int = 1): Seq[Int] = {
    require(binWithPeak < fftSize, "Index of expected peak can not be larger than fftSize")
    calcExpectedFFTOut(fftSize, binWithPeak, scale).map(c => c.abs.toInt).toSeq
  }
  
  /**
  * Check FFT error
  */
  def checkFFTError(expected: Seq[Complex], received: Seq[Complex], tolerance: Int = 2) {
    expected.zip(received).foreach {
      case (in, out) => 
        require(math.abs(in.real - out.real) <= tolerance & math.abs(in.imag - out.imag) <= tolerance, "Tolerance is not satisfied")
    }
  }
  
  /**
  * Check magnitude error, nco error, used also for checking output of the accumulator
  */
  def checkDataError(expected: Seq[Int], received: Seq[Int], tolerance: Int = 2) {
    expected.zip(received).foreach {
      case (in, out) => {
        require(math.abs(in - out) <= tolerance, "Tolerance is not satisfied")
      }
    }
  }
}
