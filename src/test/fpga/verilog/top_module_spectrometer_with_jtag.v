// SPDX-License-Identifier: Apache-2.0

`timescale 1ns / 1ps

module top_module_spectrometer_with_jtag(
    input clk,
    input reset,
    input out_ready,
    input in_valid, 
    input in_last,
   (*mark_debug*) input uRx,
   (*mark_debug = "true"*) output [7:0] out_data,
   (*mark_debug*)  output uTx
    //output out_valid,
    //output out_last
    );
    wire uTx_tmp;
    wire uRx_tmp;
   (*mark_debug = "true"*)wire        out_valid;
   (*mark_debug = "true"*)wire        out_last;
    wire [7:0]   in_data;
   (*mark_debug = "true"*) wire         in_ready;
   (*mark_debug = "true"*) wire         int_out;
    wire         xioMem_0_aw_ready;
    wire         xioMem_0_aw_valid;
    wire         xioMem_0_aw_bits_id;
    wire  [31:0] xioMem_0_aw_bits_addr;
    wire  [7:0]  xioMem_0_aw_bits_len;
    wire  [2:0]  xioMem_0_aw_bits_size;
    wire  [1:0]  xioMem_0_aw_bits_burst;
    wire         xioMem_0_aw_bits_lock;
    wire  [3:0]  xioMem_0_aw_bits_cache;
    wire  [2:0]  xioMem_0_aw_bits_prot;
    wire  [3:0]  xioMem_0_aw_bits_qos;
    wire        xioMem_0_w_ready;
    wire         xioMem_0_w_valid;
    wire  [31:0] xioMem_0_w_bits_data;
    wire  [3:0]  xioMem_0_w_bits_strb;
    wire         xioMem_0_w_bits_last;
    wire         xioMem_0_b_ready;
    wire        xioMem_0_b_valid;
    wire        xioMem_0_b_bits_id;
    wire [1:0]  xioMem_0_b_bits_resp;
    wire        xioMem_0_ar_ready;
    wire         xioMem_0_ar_valid;
    wire         xioMem_0_ar_bits_id;
    wire  [31:0] xioMem_0_ar_bits_addr;
    wire  [7:0]  xioMem_0_ar_bits_len;
    wire  [2:0]  xioMem_0_ar_bits_size;
    wire  [1:0]  xioMem_0_ar_bits_burst;
    wire         xioMem_0_ar_bits_lock;
    wire  [3:0]  xioMem_0_ar_bits_cache;
    wire  [2:0]  xioMem_0_ar_bits_prot;
    wire  [3:0]  xioMem_0_ar_bits_qos;
    wire         xioMem_0_r_ready;
    wire        xioMem_0_r_valid;
    wire        xioMem_0_r_bits_id;
    wire [31:0] xioMem_0_r_bits_data;
    wire [1:0]  xioMem_0_r_bits_resp;
    wire        xioMem_0_r_bits_last;
    wire not_reset = ~reset;
    assign uTx = uTx_tmp;
    assign uRx_tmp = uRx; 
    //assign in_data = 7'h00000000;
    
    SpectrometerTestWithoutLA specTest(
        .clock(clk),
        .reset(reset),
        .inStream_0_ready(in_ready), //out
        .inStream_0_valid(in_valid),
        .inStream_0_bits_data(in_data),
        .inStream_0_bits_last(in_last),
        .int_0(int_out), // out
        .uTx(uTx_tmp),   // out
        .uRx(uRx_tmp),    // in
        .outStream_0_ready(out_ready),
        .outStream_0_valid(out_valid),
        .outStream_0_bits_data(out_data),
        .outStream_0_bits_last(out_last),
        .ioMem_0_aw_ready(xioMem_0_aw_ready),
        .ioMem_0_aw_valid(xioMem_0_aw_valid),
        .ioMem_0_aw_bits_id(xioMem_0_aw_bits_id),
        .ioMem_0_aw_bits_addr(xioMem_0_aw_bits_addr),
        .ioMem_0_aw_bits_len(xioMem_0_aw_bits_len),
        .ioMem_0_aw_bits_size(xioMem_0_aw_bits_size),
        .ioMem_0_aw_bits_burst(xioMem_0_aw_bits_burst),
        .ioMem_0_aw_bits_lock(xioMem_0_aw_bits_lock),
        .ioMem_0_aw_bits_cache(xioMem_0_aw_bits_cache),
        .ioMem_0_aw_bits_prot(xioMem_0_aw_bits_prot),
        .ioMem_0_aw_bits_qos(xioMem_0_aw_bits_qos),
        .ioMem_0_w_ready(xioMem_0_w_ready),
        .ioMem_0_w_valid(xioMem_0_w_valid),
        .ioMem_0_w_bits_data(xioMem_0_w_bits_data),
        .ioMem_0_w_bits_strb(xioMem_0_w_bits_strb),
        .ioMem_0_w_bits_last(xioMem_0_w_bits_last),
        .ioMem_0_b_ready(xioMem_0_b_ready),
        .ioMem_0_b_valid(xioMem_0_b_valid),
        .ioMem_0_b_bits_id(xioMem_0_b_bits_id),
        .ioMem_0_b_bits_resp(xioMem_0_b_bits_resp),
        .ioMem_0_ar_ready(xioMem_0_ar_ready),
        .ioMem_0_ar_valid(xioMem_0_ar_valid),
        .ioMem_0_ar_bits_id(xioMem_0_ar_bits_id),
        .ioMem_0_ar_bits_addr(xioMem_0_ar_bits_addr),
        .ioMem_0_ar_bits_len(xioMem_0_ar_bits_len),
        .ioMem_0_ar_bits_size(xioMem_0_ar_bits_size),
        .ioMem_0_ar_bits_burst(xioMem_0_ar_bits_burst),
        .ioMem_0_ar_bits_lock(xioMem_0_ar_bits_lock),
        .ioMem_0_ar_bits_cache(xioMem_0_ar_bits_cache),
        .ioMem_0_ar_bits_prot(xioMem_0_ar_bits_prot),
        .ioMem_0_ar_bits_qos(xioMem_0_ar_bits_qos),
        .ioMem_0_r_ready(xioMem_0_r_ready),
        .ioMem_0_r_valid(xioMem_0_r_valid),
        .ioMem_0_r_bits_id(xioMem_0_r_bits_id),
        .ioMem_0_r_bits_data(xioMem_0_r_bits_data),
        .ioMem_0_r_bits_resp(xioMem_0_r_bits_resp),
        .ioMem_0_r_bits_last(xioMem_0_r_bits_last)
    );
    
    vio_0 virtual_input_output(
      .clk(clk),
      .probe_out0(in_data)
       );
    
    jtag_axi_0 jtag_to_axi (
      .aclk(clk),                       // input wire aclk
      .aresetn(not_reset),              // input wire aresetn
      .m_axi_awid(xioMem_0_aw_bits_id),        // output wire [0 : 0] m_axi_awid
      .m_axi_awaddr(xioMem_0_aw_bits_addr),    // output wire [31 : 0] m_axi_awaddr
      .m_axi_awlen(xioMem_0_aw_bits_len),      // output wire [7 : 0] m_axi_awlen
      .m_axi_awsize(xioMem_0_aw_bits_size),    // output wire [2 : 0] m_axi_awsize
      .m_axi_awburst(xioMem_0_aw_bits_burst),  // output wire [1 : 0] m_axi_awburst
      .m_axi_awlock(xioMem_0_aw_bits_lock),    // output wire m_axi_awlock
      .m_axi_awcache(xioMem_0_aw_bits_cache),  // output wire [3 : 0] m_axi_awcache
      .m_axi_awprot(xioMem_0_aw_bits_prot),    // output wire [2 : 0] m_axi_awprot
      .m_axi_awqos(xioMem_0_aw_bits_qos),      // output wire [3 : 0] m_axi_awqos
      .m_axi_awvalid(xioMem_0_aw_valid),  // output wire m_axi_awvalid
      .m_axi_awready(xioMem_0_aw_ready),  // input wire m_axi_awready
      .m_axi_wdata(xioMem_0_w_bits_data),      // output wire [31 : 0] m_axi_wdata
      .m_axi_wstrb(xioMem_0_w_bits_strb),      // output wire [3 : 0] m_axi_wstrb
      .m_axi_wlast(xioMem_0_w_bits_last),      // output wire m_axi_wlast
      .m_axi_wvalid(xioMem_0_w_valid),    // output wire m_axi_wvalid
      .m_axi_wready(xioMem_0_w_ready),    // input wire m_axi_wready
      .m_axi_bid(xioMem_0_b_bits_id),          // input wire [0 : 0] m_axi_bid
      .m_axi_bresp(xioMem_0_b_bits_resp),      // input wire [1 : 0] m_axi_bresp
      .m_axi_bvalid(xioMem_0_b_valid),    // input wire m_axi_bvalid
      .m_axi_bready(xioMem_0_b_ready),    // output wire m_axi_bready
      .m_axi_arid(xioMem_0_ar_bits_id),        // output wire [0 : 0] m_axi_arid
      .m_axi_araddr(xioMem_0_ar_bits_addr),    // output wire [31 : 0] m_axi_araddr
      .m_axi_arlen(xioMem_0_ar_bits_len),      // output wire [7 : 0] m_axi_arlen
      .m_axi_arsize(xioMem_0_ar_bits_size),    // output wire [2 : 0] m_axi_arsize
      .m_axi_arburst(xioMem_0_ar_bits_burst),  // output wire [1 : 0] m_axi_arburst
      .m_axi_arlock(xioMem_0_ar_bits_lock),    // output wire m_axi_arlock
      .m_axi_arcache(xioMem_0_ar_bits_cache),  // output wire [3 : 0] m_axi_arcache
      .m_axi_arprot(xioMem_0_ar_bits_prot),    // output wire [2 : 0] m_axi_arprot
      .m_axi_arqos(xioMem_0_ar_bits_qos),      // output wire [3 : 0] m_axi_arqos
      .m_axi_arvalid(xioMem_0_ar_valid),  // output wire m_axi_arvalid
      .m_axi_arready(xioMem_0_ar_ready),  // input wire m_axi_arready
      .m_axi_rid(xioMem_0_r_bits_id),          // input wire [0 : 0] m_axi_rid
      .m_axi_rdata(xioMem_0_r_bits_data),      // input wire [31 : 0] m_axi_rdata
      .m_axi_rresp(xioMem_0_r_bits_resp),      // input wire [1 : 0] m_axi_rresp
      .m_axi_rlast(xioMem_0_r_bits_last),      // input wire m_axi_rlast
      .m_axi_rvalid(xioMem_0_r_valid),    // input wire m_axi_rvalid
      .m_axi_rready(xioMem_0_r_ready)    // output wire m_axi_rready
    );
endmodule
