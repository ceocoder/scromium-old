package scromium.connection

import com.codahale.guild._
import org.apache.thrift.transport.TTransportException
import java.io.IOException
import scromium.util.Log

class ActorConnection(connectionFactory : ConnectionFactory) extends ActorBehavior[Function[Connection,Any],Any] with Log {
  var connection : Connection = null
  
  override def onStart() {
    try {
      connection = connectionFactory.make
    } catch {
      case e : Exception =>
        //try again laters
        error("error creating connection", e)
    }
  }
  
  def onMessage(block : Connection => Any) : Any = {
    try {
      if (connection == null) connection = connectionFactory.make
      block(connection)
    } catch {
      case e : IOException => 
        connection = connectionFactory.make
        onMessage(block)
      case e : TTransportException =>
        connection = connectionFactory.make
        onMessage(block)
    }
  }
}

class ActorConnectionFactory(connectionFactory : ConnectionFactory) extends ActorFactory[Function[Connection,Any],Any,ActorConnection] {
  def createActor() : ActorConnection = {
    new ActorConnection(connectionFactory)
  }
}

class ActorConnectionPool(val seedHost : String,
    val seedPort : Int,
    val actors : Int,
    socketFactory : SocketFactory = new SocketFactory,
    clusterDiscovery : ClusterDiscovery = new ClusterDiscovery) extends ConnectionPool with Log {
  
  val hosts = clusterDiscovery.hosts(seedHost,seedPort)
  val connectionFactory = new ConnectionFactory(hosts, seedPort, socketFactory)
  val actorPool = new ActorPool(new ActorConnectionFactory(connectionFactory))
  actorPool.start(actors)
  
  /**
   * The price we pay for decoupling is losing type information to the actor.
   * So we do a cast here back in and out
   */
  def withConnection[T](block : Connection => T) : T = {
    actorPool.call(block).asInstanceOf[T]
  }
}