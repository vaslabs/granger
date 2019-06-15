package org.vaslabs.granger.system

import java.io.File

/**
 * Created by vnicolaou on 02/09/17.
 */
trait BaseDirProvider {

  def baseDir(): File

}
