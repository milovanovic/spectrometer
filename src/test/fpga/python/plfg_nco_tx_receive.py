# SPDX-License-Identifier: Apache-2.0

# -*- coding: utf-8 -*-
"""
Created on Mon Dec  7 11:57:38 2020

@author_1: Marija Petrovic

"""
import numpy as np
import matplotlib.pyplot as plt
import serial


ser = serial.Serial('COM7') 
ser.baudrate = 115200
ser.stopbit = 1

print("Open port = " , ser.is_open)
print("Stop bit = " , ser.stopbit)
print("Baudrate is = ", ser.baudrate)

ser.timeout = 30 #timeout in seconds
print("Timeout = ", ser.timeout)
print("\n")


N = 128 

read_data = ser.read(N*4)
print(*read_data)
ser.close()

print("Total number of read_data is =", len(read_data));


output = [0]*N*2 # save sin and cos 
k = 0
if (len(read_data) > 0):
    for i in range (0, N*2):
        output[i] = read_data[k] + read_data[k+1]*256
        k = k + 2
        if (output[i] < 32768):
            output[i] = output[i] #/ 2**(14-LOG2N)
        else:
            output[i] = output[i] - 65536;

    sine_out = [0]*N
    cosine_out = [0]*N
    k = 0
    for i in range (0, N):
        sine_out[i] = output[k]
        cosine_out[i] = output[k+1]      
        k = k + 2
    
    x = np.arange(N)
    plt.subplot(211)
    plt.plot(x, sine_out)
    plt.title('Sine received from NCO')
    plt.show()
    plt.grid()
    
    plt.subplot(212)
    plt.plot(x, cosine_out, 'r')
    plt.title('Cosine received from NCO')
    plt.show()
    plt.grid()
       
