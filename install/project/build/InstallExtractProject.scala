/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */

import sbt._
import java.io.File

protected class InstallExtractProject(info: ProjectInfo, pluginProject: => InstallPluginProject) extends DefaultProject(info) with NoPublish
{
	override def unmanagedClasspath = super.unmanagedClasspath +++ info.sbtClasspath
	private lazy val plugin = pluginProject
	val mainClassName = "sbt.extract.Main"
	val proguardConfigurationPath: Path = outputPath / "proguard.pro"
	val toolsConfig = config("tools")
	val defaultConfig = Configurations.Default
	val proguardJar = "net.sf.proguard" % "proguard" % "4.3" % "tools"
	def rootProjectDirectory = rootProject.info.projectPath
	def outputJar = (plugin.outputPath ##) / defaultJarName
	
	/******** Proguard *******/
	lazy val proguard = proguardTask dependsOn(`package`, writeProguardConfiguration, cleanProguard)
	lazy val writeProguardConfiguration = writeProguardConfigurationTask dependsOn `package`
	lazy val cleanProguard = cleanTask(outputJar)
	private def proguardTask =
		task
		{
			val proguardClasspathString = Path.makeString(managedClasspath(toolsConfig).get)
			val configFile = proguardConfigurationPath.asFile.getAbsolutePath
			val exitValue = Process("java", List("-Xmx256M", "-cp", proguardClasspathString, "proguard.ProGuard", "@" + configFile)) ! log
			if(exitValue == 0) None else Some("Proguard failed with nonzero exit code (" + exitValue + ")")
		}
	private def writeProguardConfigurationTask =
		task
		{
			// the template for the proguard configuration file
			val outTemplate = """
				|-dontoptimize
				|-dontobfuscate
				|-dontnote
				|-dontwarn
				|-libraryjars %s
				|%s
				|-outjars %s
				|-ignorewarnings
				|-keep public class %s {
				|    public static void main(java.lang.String[]);
				|}"""
			
			val defaultJar = jarPath.absolutePath
			log.debug("proguard configuration using main jar " + defaultJar)
			val externalDependencies = (mainCompileConditional.analysis.allExternals).map(_.getAbsoluteFile).filter(_.getName.endsWith(".jar"))
			debugJars("external dependencies", externalDependencies)
			// partition jars from the external jar dependencies of this project by whether they are located in the project directory
			// if they are, they are specified with -injars, otherwise they are specified with -libraryjars
			val (externalJars, libraryJars) = externalDependencies.toList.partition{jar => Path.relativize(rootProjectDirectory, jar).isDefined}
			debugJars("library jars", libraryJars)
			val externalJarStrings = externalJars.map( _ + "(!META-INF/**,!*.properties)")
			// exclude properties files and manifests from scala-library jar
			val inJars = (defaultJar :: externalJarStrings).map("-injars " + _).mkString("\n")
			
			val proguardConfiguration = outTemplate.stripMargin.format(libraryJars.mkString(File.pathSeparator), inJars, outputJar.absolutePath, mainClassName)
			log.debug("Proguard configuration written to " + proguardConfigurationPath)
			FileUtilities.write(proguardConfigurationPath.asFile, proguardConfiguration, log)
		}
	private def debugJars[T](label: String, jars: Iterable[T]): Unit =
		log.debug("proguard configuration " + label + ": \n\t" + jars.mkString("\n\t"))
}