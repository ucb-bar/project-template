package chipyard

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.rocket.{DCacheParams}

class AbstractTraceGenConfig extends Config(
  new chipyard.harness.WithBlackBoxSimMem ++
  new chipyard.harness.WithTraceGenSuccess ++
  new chipyard.harness.WithClockAndResetFromHarness ++
  new chipyard.iobinders.WithAXI4MemPunchthrough ++
  new chipyard.iobinders.WithTraceGenSuccessPunchthrough ++
  new chipyard.iobinders.WithDividerOnlyClockGenerator ++
  new chipyard.config.WithTracegenSystem ++
  new chipyard.config.WithNoSubsystemDrivenClocks ++
  new chipyard.config.WithPeripheryBusFrequencyAsDefault ++
  new chipyard.config.WithMemoryBusFrequency(100.0) ++
  new chipyard.config.WithPeripheryBusFrequency(100.0) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.groundtest.GroundTestBaseConfig)


class TraceGenConfig extends Config(
  new tracegen.WithTraceGen()(List.fill(2) { DCacheParams(nMSHRs = 0, nSets = 16, nWays = 2) }) ++
  new AbstractTraceGenConfig)

class NonBlockingTraceGenConfig extends Config(
  new tracegen.WithTraceGen()(List.fill(2) { DCacheParams(nMSHRs = 2, nSets = 16, nWays = 2) }) ++
  new AbstractTraceGenConfig)

class BoomTraceGenConfig extends Config(
  new tracegen.WithBoomTraceGen()(List.fill(2) { DCacheParams(nMSHRs = 8, nSets = 16, nWays = 2) }) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new AbstractTraceGenConfig)

class NonBlockingTraceGenL2Config extends Config(
  new tracegen.WithL2TraceGen()(List.fill(2)(DCacheParams(nMSHRs = 2, nSets = 16, nWays = 4))) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new AbstractTraceGenConfig)

class NonBlockingTraceGenL2RingConfig extends Config(
  new tracegen.WithL2TraceGen()(List.fill(2)(DCacheParams(nMSHRs = 2, nSets = 16, nWays = 4))) ++
  new testchipip.WithRingSystemBus ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new AbstractTraceGenConfig)
