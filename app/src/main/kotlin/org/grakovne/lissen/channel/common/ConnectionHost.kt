package org.grakovne.lissen.channel.common

enum class ConnectionType {
  INTERNAL,
  EXTERNAL,
}

data class ConnectionHost(
  val url: String,
  val type: ConnectionType,
) {
  companion object {
    fun external(url: String) = ConnectionHost(url = url, type = ConnectionType.EXTERNAL)

    fun internal(url: String) = ConnectionHost(url = url, type = ConnectionType.INTERNAL)
  }
}
