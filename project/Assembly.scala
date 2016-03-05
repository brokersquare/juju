import sbt._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy
    
object Assembly {
    lazy val settings = Seq(
        assemblyMergeStrategy in assembly := customMergeStrategy // Use the customMergeStrategy in your settings
    )

    // Create a new MergeStrategy for aop.xml files
    val aopMerge: MergeStrategy = new MergeStrategy {
        val name = "aopMerge"
        import scala.xml._
        import scala.xml.dtd._
        
        def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
            val dt = DocType("aspectj", PublicID("-//AspectJ//DTD//EN", "http://www.eclipse.org/aspectj/dtd/aspectj.dtd"), Nil)
            val file = MergeStrategy.createMergeTarget(tempDir, path)
            val xmls: Seq[Elem] = files.map(XML.loadFile)
            val aspectsChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "aspects" \ "_")
            val weaverChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "weaver" \ "_")
            val options: String = xmls.map(x => (x \\ "aspectj" \ "weaver" \ "@options").text).mkString(" ").trim
            val weaverAttr = if (options.isEmpty) Null else new UnprefixedAttribute("options", options, Null)
            val aspects = new Elem(null, "aspects", Null, TopScope, false, aspectsChildren: _*)
            val weaver = new Elem(null, "weaver", weaverAttr, TopScope, false, weaverChildren: _*)
            val aspectj = new Elem(null, "aspectj", Null, TopScope, false, aspects, weaver)
            XML.save(file.toString, aspectj, "UTF-8", xmlDecl = false, dt)
            IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
            Right(Seq(file -> path))
        }
    }
    

    
    // Use defaultMergeStrategy with a case for aop.xml
    // I like this better than the inline version mentioned in assembly's README
    val customMergeStrategy: String => MergeStrategy = {
        case PathList("META-INF", "aop.xml") =>
            aopMerge
        case s =>
            MergeStrategy.defaultMergeStrategy(s)
    }
}