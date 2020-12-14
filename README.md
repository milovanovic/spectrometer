Digital Spectrometer Generator designed in Chisel HDL
======================================================
[![Build Status](https://travis-ci.org/milovanovic/spectrometer.svg?branch=master)](https://travis-ci.org/milovanovic/spectrometer)

## Overview

Digital spectrometer generator is designed in [Chisel](http://www.chisel-lang.org) and it contains:

* Single-path Delay Feedback Fast Fourier Transform (SDF-FFT) accelerator
* Logarithm-Magnitude circuitry
* Spectral Accumulator
* Built-In Self-Test (BIST) consisting of: 
	* Numerically Controlled Oscillator (NCO), preceeded by 
	* Piecewise Linear Function Generator (PLFG) 
* A lot of mux- & spliter-like test structures together with WB2AXI bridge, UART, etc.
