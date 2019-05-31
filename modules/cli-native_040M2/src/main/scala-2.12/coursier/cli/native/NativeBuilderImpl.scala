package coursier.cli.native

import java.io.File
import java.nio.file.Path
import scala.scalanative.{build => sn}

class NativeBuilderImpl extends NativeBuilder {
  def build(
    mainClass: String,
    files: Seq[File],
    output0: File,
    params: NativeLauncherParams,
    log: String => Unit,
    verbosity: Int
  ): Unit = {

    val workdir = params.workDir

    val classpath: Seq[Path] = files.map(_.toPath)
    val main: String         = mainClass + "$"
    val outpath              = output0.toPath

    val mode = params.modeOpt match {
      case Some("debug") => sn.Mode.debug
      case Some("release") => sn.Mode.release
      case Some("release-fast") => sn.Mode.releaseFast
      case Some("release-full") => sn.Mode.releaseFull
      case Some("default") => sn.Mode.default
      case Some(other) => throw new Exception(s"Unrecognized native mode '$other'")
      case None => sn.Mode.default
    }

    val gc = params.gcOpt match {
      case Some("default") => sn.GC.default
      case Some("none") => sn.GC.none
      case Some("boehm") => sn.GC.boehm
      case Some("immix") => sn.GC.immix
      case Some(other) => throw new Exception(s"Unrecognized native GC '$other'")
      case None => sn.GC.default
    }

    val clang = params.clangOpt.getOrElse {
      sn.Discover.clang()
    }
    val clangpp = params.clangppOpt.getOrElse {
      sn.Discover.clangpp()
    }

    val linkingOptions =
      (if (params.prependDefaultLinkingOptions) sn.Discover.linkingOptions() else Nil) ++
        params.linkingOptions
    val compileOptions =
      (if (params.prependDefaultCompileOptions) sn.Discover.compileOptions() else Nil) ++
        params.compileOptions

    val config = sn.Config.empty
      .withGC(gc)
      .withMode(mode)
      .withLinkStubs(params.linkStubs)
      .withClang(clang)
      .withClangPP(clangpp)
      .withLinkingOptions(linkingOptions)
      .withCompileOptions(compileOptions)
      .withTargetTriple(params.targetTripleOpt.getOrElse {
        sn.Discover.targetTriple(clang, params.workDir)
      })
      .withNativelib(params.nativeLibOpt.getOrElse(
        sn.Discover.nativelib(files.map(_.toPath)).get
      ))
      .withMainClass(main)
      .withClassPath(classpath)
      .withWorkdir(workdir)

    try sn.Build.build(config, outpath)
    finally {
      if (!params.keepWorkDir)
        deleteRecursive(workdir.toFile)
    }
  }

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory) {
      f.listFiles().foreach(deleteRecursive)
    }
    f.delete()
  }
}