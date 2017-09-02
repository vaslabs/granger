package org.vaslabs.granger.system

import org.vaslabs.granger.github.releases.ReleaseTag

/**
  * Created by vnicolaou on 01/08/17.
  */
case class UpdateConfig(url: String = "https://api.github.com/repos/vaslabs/granger/releases", currentRelease: ReleaseTag = ReleaseTag("1.4"))