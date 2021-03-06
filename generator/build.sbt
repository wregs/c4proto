

scalaVersion := "2.12.8"

libraryDependencies += "org.scalameta" %% "scalameta" % "4.1.0"

enablePlugins(JavaServerAppPackaging)

description := s"C4 framework / scalameta generator for rules and Protobuf adapters"
organization := "ee.cone"
version := "0.E.5.1"
bintrayRepository := "c4proto"
licenses := ourLicense

lazy val ourLicense = Seq("Apache-2.0" -> url("http://opensource.org/licenses/Apache-2.0"))


