package com.haskforce.run.stack.task

import java.io.File
import java.nio.file.Files
import java.util.regex.Pattern

import com.haskforce.settings.HaskellBuildSettings
import com.haskforce.utils.FileUtil
import com.intellij.execution.configurations.{CommandLineState, GeneralCommandLine, ParametersList}
import com.intellij.execution.filters._
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.{OSProcessHandler, ProcessEvent, ProcessListener}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.{ConsoleView, ConsoleViewContentType}
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem

import scala.collection.JavaConverters._

class StackTaskCommandLineState(
  environment: ExecutionEnvironment,
  config: StackTaskConfiguration
) extends CommandLineState(environment) {

  setConsoleBuilder(new TextConsoleBuilderImpl(config.getProject) {
    override def getConsole: ConsoleView = {
      val consoleView = new ConsoleViewImpl(config.getProject, true)
      consoleView.addMessageFilter(new PatternBasedFileHyperlinkFilter(
        config.getProject,
        config.getProject.getBasePath,
        new RelativeDiscoveryFileHyperlinkRawDataFinder(getProject, Array(
          new PatternHyperlinkFormat(
            StackTaskCommandLineState.LINK_TO_SOURCE_REGEX, false, false,
            PatternHyperlinkPart.PATH, PatternHyperlinkPart.LINE, PatternHyperlinkPart.COLUMN
          )
        ))
      ))
      consoleView
    }
  })

  override def startProcess(): OSProcessHandler = {
    val configState = config.getConfigState
    val commandLine: GeneralCommandLine = new GeneralCommandLine
    // Set up the working directory for the process
    // TODO: Make this configurable
    commandLine.setWorkDirectory(getEnvironment.getProject.getBasePath)
    val buildSettings: HaskellBuildSettings = HaskellBuildSettings.getInstance(config.getProject)
    // Set the path to `stack`
    commandLine.setExePath(buildSettings.getStackPath)
    // Build the parameters list
    val parametersList: ParametersList = commandLine.getParametersList
    parametersList.addParametersString(configState.task)
    // Set the env vars
    val environment = commandLine.getEnvironment
    if (configState.environmentVariables.isPassParentEnvs) environment.putAll(System.getenv())
    if (configState.useCurrentSSHAgentVars) environment.putAll(extractCurrentSSHAgentVars())
    commandLine.getEnvironment.putAll(configState.environmentVariables.getEnvs)
    // Start and return the process
    val procHandler = new OSProcessHandler(commandLine)
    // TODO: This doesn't seem to work...the message doesn't get printed...
    procHandler.addProcessListener(new ProcessListener {
      override def startNotified(event: ProcessEvent): Unit = ()
      override def processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean): Unit = ()
      override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = ()
      override def processTerminated(event: ProcessEvent): Unit = {
        if (event.getExitCode == 0) {
          getConsoleBuilder.getConsole.print(
            s"Stack task '${config.getConfigState.task}' completed successfully (exit code 0)",
            ConsoleViewContentType.NORMAL_OUTPUT
          )
        }
      }
    })
    procHandler
  }

  private def extractCurrentSSHAgentVars(): java.util.Map[String, String] = {
    def empty = java.util.Collections.emptyMap[String, String]()
    val home = sys.props.get("user.home").getOrElse { return empty }
    val sshDir = new File(home, ".ssh")
    if (!sshDir.isDirectory) return empty
    val envFile = sshDir.listFiles().find(_.getName.startsWith("environment-")).getOrElse { return empty }
    Files.readAllLines(envFile.toPath).iterator().asScala
      .filter(s => s.startsWith("SSH_AUTH_SOCK=") || s.startsWith("SSH_AGENT_PID="))
      .map(_.split(';').head.split('=') match {
        case Array(k, v) => (k, v)
        case _ => return empty
      })
      .toMap.asJava
  }
}

object StackTaskCommandLineState {
  // [\\w\\-]*>? detects a dependency building; issue #409
  private val LINK_TO_SOURCE_REGEX = Pattern.compile("^[\\w\\-\\s]*>?\\s*([^:]+):(\\d+):(\\d+):")
}

/**
  * Attempts to discover relative paths and convert them into canonical hyperlinks.
  * Normal absolute paths are unaffected by this process.
  */
class RelativeDiscoveryFileHyperlinkRawDataFinder(
  project: Project,
  linkFormats: Array[PatternHyperlinkFormat]
) extends PatternBasedFileHyperlinkRawDataFinder(linkFormats) {

  override def find(line: String): java.util.List[FileHyperlinkRawData] = {
    val res = super.find(line)
    if (res.isEmpty) return res
    val fs = LocalFileSystem.getInstance()
    res.iterator().asScala.foreach { data =>
      if (fs.findFileByPath(data.getFilePath) != null) return res
    }
    // Infer relative path, would be nice if we could figure this out somehow from the command
    // or output to determine base relative dir instead of just guessing like this.
    res.iterator().asScala.map { data =>
      val found = FileUtil.findFilesRecursively(
        ProjectUtil.guessProjectDir(project),
        _.getCanonicalPath.endsWith(data.getFilePath)
      )
      // Abort if we found 0 or more than 1 match since that would be ambiguous.
      if (found.length != 1) None else {
        Some(new FileHyperlinkRawData(
          found.head.getCanonicalPath,
          data.getDocumentLine,
          data.getDocumentColumn,
          data.getHyperlinkStartInd,
          data.getHyperlinkEndInd
        ))
      }
    }.collectFirst {
      case Some(x) => java.util.Collections.singletonList(x)
    }.getOrElse(res)
  }
}
