lazy val baseName       = "NeovimSwing"
lazy val baseNameL      = "neovim-swing"
lazy val projectVersion = "0.1.0-SNAPSHOT"
lazy val mimaVersion    = "0.1.0"
lazy val gitRepoHost    = "github.com"
lazy val gitRepoUser    = "Sciss"

ThisBuild / version      := projectVersion
ThisBuild / organization := "de.sciss"

lazy val deps = new {
  val main = new {
    val airframe  = "21.8.0"
    val model     = "0.3.5"
    val scallop   = "4.0.3"
    val swing     = "3.0.0"
  }
}

lazy val root = project.withId(baseNameL).in(file("."))
  .settings(publishSettings)
  .settings(
//    name                := baseName,
    description         := "Embedding Neovim in a Swing/Java2D user interface",
    homepage            := Some(url(s"https://$gitRepoHost/$gitRepoUser/$baseName")),
    licenses            := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")),
    scalaVersion        := "2.13.6",
    crossScalaVersions  := Seq("3.0.1", "2.13.6", "2.12.14"),
    libraryDependencies ++= Seq(
      "de.sciss"                %% "model"            % deps.main.model,          // message dispatch
      "org.scala-lang.modules"  %% "scala-swing"      % deps.main.swing,          // UI
      "org.wvlet.airframe"      %% "airframe-msgpack" % deps.main.airframe,       // msgpack
      "org.rogach"              %% "scallop"          % deps.main.scallop % Test, // command line option parsing
    )
  )

// ---- publishing ----

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false },
  developers := List(
    Developer(
      id    = "sciss",
      name  = "Hanns Holger Rutz",
      email = "contact@sciss.de",
      url   = url("https://www.sciss.de")
    )
  ),
  scmInfo := {
    Some(ScmInfo(
      url(s"https://$gitRepoHost/$gitRepoUser/$baseName"),
      s"scm:git@$gitRepoHost:$gitRepoUser/$baseName.git"
    ))
  },
)
