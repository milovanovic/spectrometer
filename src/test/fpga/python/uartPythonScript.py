# -*- coding: utf-8 -*-
"""
@author_1: Marija Petrović

"""

import numpy as np
import matplotlib.pyplot as plt
import binascii
import serial
import math


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

LOG2N = 6;  # number of stages
N = 2**LOG2N; # fft size 
M = 64; #3*N;  # total number of samples
num_win = 5; # number of fft windows that should be sent to FPGA board

x = np.arange(N)#np.arange(M)
x1 = np.arange(M/4);
x2 = np.arange (M/3);

f1= 2
f2 = 5
f3 = 7
f4 = 10
fs = 64

sine1 = np.sin(2 * np.pi * x * f2/fs)
#sine1 = np.sin(2 * np.pi * x1 * f1/fs) + np.cos(2 * np.pi * x1 * f2/fs)
sine2 = np.sin(2 * np.pi * x2 * f3/fs) +  np.cos(2 * np.pi * x2 * f4/fs)

#real_samples = np.append(sine1, sine2)
real_samples = sine1 * 0.9
imag_samples = np.zeros(len(real_samples))

#append_zeros = np.zeros(M-len(real_samples))
#real_samples = np.append(real_samples, append_zeros);
#imag_samples = np.append(imag_samples, append_zeros);
#real_samples = real_samples / (max(abs(real_samples))) * 0.9  # scale, be sure that it is not larger than 1 and smaller than -1

plt.figure()
plt.title('Input signal')
plt.plot(x, real_samples)
plt.show()

scaled_data_r = real_samples*math.pow(2,15)
scaled_data_r = np.rint(scaled_data_r)
scaled_data_r = scaled_data_r.astype(int)
scaled_data_i = imag_samples*math.pow(2,15)
scaled_data_i = np.rint(scaled_data_i)
scaled_data_i = scaled_data_i.astype(int)

complex_in = [complex]*M
for i in range (0, M):
    complex_in[i] = complex(scaled_data_r[i], scaled_data_i[i])

python_fft = np.fft.fft(complex_in)

plt.figure()
plt.title(" Python FFT ")
plt.plot(x, np.abs(python_fft))
plt.grid()
plt.show()

print(*np.abs(python_fft))

bytes_array_r = [0]*M
bytes_array_i = [0]*M
transmit_data = bytearray()

for k in range (0,num_win):
    for i in range (0,M):
        bytes_array_r[i] = int(scaled_data_r[i]);
        bytes_array_r[i] = bytes_array_r[i].to_bytes(2, byteorder='little', signed=True)
        bytes_array_i[i] = int(scaled_data_i[i]);
        bytes_array_i[i] = bytes_array_i[i].to_bytes(2, byteorder='little', signed=True)
        transmit_data += bytes_array_i[i]
        transmit_data += bytes_array_r[i]
    
#send_to_fpga = []
#send_to_fpga += num_win*[transmit_data]
print ("Input on RX port of the FPGA: \n", binascii.hexlify(transmit_data))
print("\n")

# send data stream
ser.write(transmit_data)

# receive data stream
read_data = ser.read(N*4)
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
    bitreverse_mag = get_bit_reversed_list(mag)
    
    x = np.arange(N)
    plt.figure()
    plt.title('FFT received from the FPGA board')
    plt.grid()
    plt.plot(x, bitreverse_mag)
    plt.show()
    print(*bitreverse_mag)
