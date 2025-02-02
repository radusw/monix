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

private[reactive] final class ReduceOperator[A](op: (A, A) => A) extends Operator[A, A] {

  def apply(out: Subscriber[A]): Subscriber[A] =
    new Subscriber[A] {
      implicit val scheduler = out.scheduler

      private[this] var isDone = false
      private[this] var state: A = _
      private[this] var isFirst = true

      def onNext(elem: A): Future[Ack] = {
        try {
          if (isFirst) {
            isFirst = false
            state = elem
          } else {
            state = op(state, elem)
          }

          Continue
        } catch {
          case ex if NonFatal(ex) =>
            onError(ex)
            Stop
        }
      }

      def onComplete(): Unit =
        if (!isDone) {
          isDone = true
          if (!isFirst) out.onNext(state)
          out.onComplete()
        }

      def onError(ex: Throwable): Unit =
        if (!isDone) {
          isDone = true
          out.onError(ex)
        }
    }
}
