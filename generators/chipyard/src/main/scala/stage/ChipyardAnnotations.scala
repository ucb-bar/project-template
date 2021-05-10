// See LICENSE for license details.
// Based on Rocket Chip's stage implementation

package chipyard.stage

import freechips.rocketchip.stage.ConfigsAnnotation
import firrtl.options.{HasShellOptions, ShellOption}

/** This hijacks the existing ConfigAnnotation to accept the legacy _-delimited format  */
private[stage] object UnderscoreDelimitedConfigsAnnotation extends HasShellOptions {
  override val options = Seq(
    new ShellOption[String](
      longOption = "legacy-configs",
      toAnnotationSeq = a => {
        val split = a.split(':')
        assert(split.length == 2, s"'${a}' split by ':' doesn't yield two things")
        val packageName = split.head
        val configs     = split.last.split("_")
        Seq(new ConfigsAnnotation(configs map { config => if (config contains ".") s"${config}" else s"${packageName}.${config}" } ))
      },
      helpText = "A string of underscore-delimited configs (configs have decreasing precendence from left to right).",
      shortOption = Some("LC")
    )
  )
}

