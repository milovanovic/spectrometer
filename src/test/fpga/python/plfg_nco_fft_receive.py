# -*- coding: utf-8 -*-
"""
Created on Mon Dec  7 12:25:13 2020

@author_1: Marija Petrovic

"""

import numpy as np
import matplotlib.pyplot as plt
import serial


def bit_reverse_traverse(a):
    n = a.shape[0]
    assert(not n&(n-1) ) # assert that n is a power of 2

    if n == 1:
        yield a[0]
    else:
        even_index = [int(x) for x in np.arange(n/2)*2]
        odd_index = [int(x) for x in np.arange(n/2)*2 + 1]
        for even in bit_reverse_traverse(a[even_index]):
            yield even
        for odd in bit_reverse_traverse(a[odd_index]):
            yield odd
            
def get_bit_reversed_list(l):
    n = len(l)

    indexs = np.arange(n)
    b = []
    for i in bit_reverse_traverse(indexs):
        b.append(l[i])
    return b

plt.ion()
np.set_printoptions(suppress=True)

ser = serial.Serial('COM7') 
ser.baudrate = 115200
ser.stopbit = 1

print("Open port = " , ser.is_open)
print("Stop bit = " , ser.stopbit)
print("Baudrate is = ", ser.baudrate)

ser.timeout = 30 #timeout in seconds
print("Timeout = ", ser.timeout)
print("\n")

#Settings
N = 128 #+ 2 first two data 
#N = 128
expected_peak = 2 # this is what we expect
bit_reverse = False

read_data = ser.read(N*4+8)[8:] # this is necessary while some issues occur somewhere after out_mux
#read_data = ser.read(N*4)

print(*read_data)
ser.close()

print("Total number of read_data is =", len(read_data));

output = [0]*N*2 #save real and imag part 
k = 0
if (len(read_data) > 0):
    for i in range (0, N*2):
        output[i] = read_data[k] + read_data[k+1]*256
        k = k + 2
        if (output[i] < 32768):
            output[i] = output[i] #/ 2**(14-LOG2N)
        else:
            output[i] = output[i] - 65536;

        complex_out = [0]*N
    k = 0
    for i in range (0, N):
        complex_out[i] = complex(output[k], output[k+1])
        k = k + 2
    mag = np.absolute(complex_out);
    # if (bit_reverse):
    first_win = get_bit_reversed_list(mag[:64])
    second_win = get_bit_reversed_list(mag[64:])
    bitreverse_mag = np.append(first_win, second_win)
    
    x = np.arange(N)
    plt.figure()
    plt.title('FFT received from the FPGA board')
    plt.grid()
    plt.plot(x, bitreverse_mag)
    plt.show()
    print(*mag)
