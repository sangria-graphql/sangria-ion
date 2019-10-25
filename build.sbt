name := "sangria-ion"
organization := "org.sangria-graphql"
version := "2.0.0-SNAPSHOT"
mimaPreviousArtifacts := Set("org.sangria-graphql" %% "sangria-ion" % "1.0.0")

description := "Sangria Amazon Ion marshalling"
homepage := Some(url("http://sangria-graphql.org"))
licenses := Seq("Apache License, ASL Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

scalaVersion := "2.12.10"
crossScalaVersions := Seq("2.11.12", "2.12.10", "2.13.0")

scalacOptions ++= Seq("-deprecation", "-feature")

scalacOptions ++= {
  if (scalaVersion.value startsWith "2.11")
    Seq("-target:jvm-1.7")
  else
    Seq.empty
}

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria-marshalling-api" % "1.0.4",

  "software.amazon.ion" % "ion-java" % "1.5.1",

  "org.sangria-graphql" %% "sangria-marshalling-testkit" % "1.0.2" % Test,
  "org.scalatest" %% "scalatest" % "3.0.8" % Test
)

git.remoteRepo := "git@github.com:sangria-graphql/sangria-ion.git"

// Publishing

publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := (_ => false)
publishTo := Some(
  if (version.value.trim.endsWith("SNAPSHOT"))
    "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")

// Site and docs

enablePlugins(GhpagesPlugin)
enablePlugins(SiteScaladocPlugin)

// nice *magenta* prompt!

shellPrompt in ThisBuild := { state =>
  scala.Console.MAGENTA + Project.extract(state).currentRef.project + "> " + scala.Console.RESET
}

// Additional meta-info

startYear := Some(2016)
organizationHomepage := Some(url("https://github.com/sangria-graphql"))
developers := Developer("OlegIlyenko", "Oleg Ilyenko", "", url("https://github.com/OlegIlyenko")) :: Nil
scmInfo := Some(ScmInfo(
  browseUrl = url("https://github.com/sangria-graphql/sangria-ion.git"),
  connection = "scm:git:git@github.com:sangria-graphql/sangria-ion.git"
))
