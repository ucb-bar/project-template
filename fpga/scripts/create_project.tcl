# Set the variable for the directory that includes all scripts

set scriptdir [file join [file dirname [info script]] "../fpga-shells/xilinx/common/tcl"]

# Set up variables and Vivado objects
source [file join $scriptdir "prologue.tcl"]

# Initialize Vivado project files
source [file join $scriptdir "init.tcl"]