lazy val root = project.in(file("."))
  .settings(
    scalaVersion := "2.13.6", // "3.0.1",
    libraryDependencies ++= Seq(
      // "com.github.xuwei-k" %% "msgpack4z-core" % "0.5.2", // needs scalaz
      // "org.velvia" %% "msgpack4s" % "0.6.0", // currently unavailable
      "com.github.xuwei-k" %% "scodec-msgpack" % "0.8.0", // needs scodec and shapeless
       "org.wvlet.airframe" %% "airframe-msgpack" % "21.8.0",
    )
  )
