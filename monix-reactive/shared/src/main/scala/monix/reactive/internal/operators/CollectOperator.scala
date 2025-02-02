/*
 * Copyright (c) 2014-2022 Monix Contributors.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.reactive.internal.operators

import monix.execution.Ack
import monix.execution.Ack.{ Continue, Stop }
import scala.util.control.NonFatal
import monix.reactive.Observable.Operator
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

private[reactive] final class CollectOperator[-A, +B](pf: PartialFunction[A, B]) extends Operator[A, B] {

  import CollectOperator.{ checkFallback, isDefined }

  def apply(out: Subscriber[B]): Subscriber[A] = {
    new Subscriber[A] {
      implicit val scheduler = out.scheduler
      private[this] var isDone = false

      def onNext(elem: A): Future[Ack] = {
        // Protects calls to user code from within the operator and
        // stream the error downstream if it happens, but if the
        // error happens because of calls to `onNext` or other
        // protocol calls, then the behavior should be undefined.
        var streamError = true
        try {
          val next = pf.applyOrElse(elem, checkFallback[B])
          if (isDefined(next)) {
            streamError = false
            out.onNext(next)
          } else
            Continue
        } catch {
          case NonFatal(ex) if streamError =>
            onError(ex)
            Stop
        }
      }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          out.onError(ex)
        }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          out.onComplete()
        }
    }
  }
}

private object CollectOperator extends (Any => Any) {
  /** In the case a partial function is not defined, return a magic fallback value. */
  def checkFallback[B]: Any => B = this.asInstanceOf[Any => B]

  /** Indicates whether the result is the magic fallback value. */
  def isDefined(result: Any): Boolean = result.asInstanceOf[AnyRef] ne this

  /** Always returns `this`, used as the magic fallback value. */
  override def apply(elem: Any): Any = this
}
