package org.vaslabs.granger

/**
  * Created by vnicolaou on 16/06/17.
  */
case class GrangerConfig(repoLocation: String, bindAddress: String = "0.0.0.0",
                         bindPort: Int = 8080,
                         keysLocation: String
                        )

object GrangerConfig {
  val Namespace = "granger"
}
