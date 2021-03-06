package aio
package conduit

import java.nio.channels.{ AsynchronousByteChannel ⇒ Channel, CompletionHandler ⇒ Handler }

import scala.concurrent.{ Future, Promise }

import buffer.ByteResult
import language.ignore

/**
 *
 */
trait ChannelConduit[C <: Channel]

  extends ChannelSourceConduit[C]

  with ChannelSinkConduit[C]

/**
 *
 */
trait ChannelSourceConduit[C <: Channel]

    extends ByteResultSourceConduit {

  protected[this] val channel: C

  final def read: Future[ByteResult] = {
    val byteresult = ByteResult.default
    val promise = Promise[ByteResult]
    object readhandler extends Handler[Integer, Null] {
      @inline def failed(e: Throwable, a: Null) = {
        cleanup
        promise.tryFailure(e)
      }
      @inline def completed(processed: Integer, a: Null) = {
        if (0 < processed.intValue) {
          byteresult.flip
          promise.trySuccess(byteresult)
        } else {
          cleanup
          promise.trySuccess(ByteResult.sentinel)
        }
      }
      @inline def cleanup: Unit = {
        byteresult.release
        ignore(channel.close)
      }
    }
    channel.read(byteresult, null: Null, readhandler)
    promise.future
  }

}

/**
 *
 */
trait ChannelSinkConduit[C <: Channel]

    extends ByteResultSinkConduit {

  protected[this] val channel: C

  final def write(byteresult: ByteResult): Future[Unit] = {
    val promise = Promise[Unit]
    object writehandler extends Handler[Integer, Null] {
      @inline def failed(e: Throwable, a: Null) = {
        cleanup
        promise.tryFailure(e)
      }
      @inline def completed(processed: Integer, a: Null) = {
        if (0 < byteresult.remaining) {
          channel.write(byteresult, null: Null, writehandler)
        } else {
          byteresult.release
          promise.trySuccess(())
        }
      }
    }
    @inline def cleanup = {
      byteresult.release
      ignore(channel.close)
    }
    if (byteresult.isLast) {
      cleanup
      promise.success(()).future
    } else {
      channel.write(byteresult, null: Null, writehandler)
      promise.future
    }
  }

}
