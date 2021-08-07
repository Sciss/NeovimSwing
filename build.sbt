lazy val baseName       = "NeovimUITest"
lazy val baseNameL      = baseName.toLowerCase()
lazy val projectVersion = "0.1.0-SNAPSHOT"
lazy val mimaVersion    = "0.1.0"

ThisBuild / version      := projectVersion
ThisBuild / organization := "de.sciss"

lazy val deps = new {
  val main = new {
    val airframe  = "21.8.0"
    val model     = "0.3.5"
    val swing     = "3.0.0"
  }
}

lazy val root = project.in(file("."))
  .settings(
    name                := baseName,
    description         := "Testing embedded Neovim with Swing/Java2D user interface",
    homepage            := Some(url(s"https://github.com/Sciss/$baseName")),
    licenses            := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")),
    scalaVersion        := "2.13.6",
    crossScalaVersions  := Seq("3.0.1", "2.13.6", "2.12.14"),
    libraryDependencies ++= Seq(
      "de.sciss"                %% "model"            % deps.main.model,      // message dispatch
      "org.scala-lang.modules"  %% "scala-swing"      % deps.main.swing,      // UI
      "org.wvlet.airframe"      %% "airframe-msgpack" % deps.main.airframe,   // msgpack
    )
  )
