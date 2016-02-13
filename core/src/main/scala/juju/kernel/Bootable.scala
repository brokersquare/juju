package juju.kernel

import java.lang.Boolean.getBoolean


trait Bootable {
  /**
   * Callback run on microkernel startup.
   * Create initial actors and messages here.
   */
  def startup(): Unit

  /**
   * Callback run on microkernel shutdown.
   * Shutdown actor systems here.
   */
  def shutdown(): Unit

  private def quiet = getBoolean("akka.kernel.quiet")
  protected def log(s: String) = if (!quiet) println(s)

  def main(args: Array[String]) = {
    injectSystemPropertiesFromArgs(args)
    
    log(banner)

    val className = this.getClass.getName

    log(s"Starting up.. $className with arguments: '${args.mkString(", ")}'")
    startup()

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run() = {
        log("")
        log("Shutting down " + className)
        shutdown()
        log("Successfully shut down " + className)
      }
    }))
  }

  private def injectSystemPropertiesFromArgs(args: Array[String]): Unit ={
    args
      .filter(_.startsWith("-D"))
      .map(_.substring("-D".length))
      .map(_.split('='))
      .filter(p=>System.getProperty(p.head) == null)
      .foreach { tokens =>
        System.setProperty(tokens.head, tokens.last)
      }
  }

  //taken from http://patorjk.com/software/taag/
  def banner = """
___________________________________________________        
 _______/\\\_____________________/\\\_______________       
  ______\///_____________________\///________________      
   _______/\\\__/\\\____/\\\_______/\\\__/\\\____/\\\_     
    ______\/\\\_\/\\\___\/\\\______\/\\\_\/\\\___\/\\\_    
     ______\/\\\_\/\\\___\/\\\______\/\\\_\/\\\___\/\\\_   
      __/\\_\/\\\_\/\\\___\/\\\__/\\_\/\\\_\/\\\___\/\\\_  
       _\//\\\\\\__\//\\\\\\\\\__\//\\\\\\__\//\\\\\\\\\__ 
        __\//////____\/////////____\//////____\/////////___                    
                       """
}