package juju.kernel

import java.lang.Boolean.getBoolean


trait Bootable {
  def args = _args
  var _args : Array[String] = _

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
    this._args = args
    log(banner)

    val className = this.getClass.getName

    log(s"Starting up.. $className with arguments: '${args.mkString(", ")}'")
    startup()

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run = {
        log("")
        log("Shutting down " + className)
        shutdown()
        log("Successfully shut down " + className)
      }
    }))
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