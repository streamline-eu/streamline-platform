package org.apache.flink.ml.parameter.server

import org.scalatest._
import prop._

class WorkerLogicTest extends FlatSpec with PropertyChecks with Matchers {

  "pull limiter" should "limit pulls" in {
    type WOut = (Int, Long)
    val worker = new WorkerLogic[Int, Long, WOut] {
      override def onRecv(data: Int, ps: ParameterServerClient[Long, WOut]): Unit = {
        ps.pull(data)
      }

      override def onPullRecv(paramId: Int,
                              paramValue: Long,
                              ps: ParameterServerClient[Long, (Int, Long)]): Unit = {

      }
    }

    val ps = new ParameterServerClient[Long, WOut] {
      var pullCounter = 0

      override def pull(id: Int): Unit = {
        pullCounter += 1
      }

      override def push(id: Int, deltaUpdate: Long): Unit = {}

      override def output(out: (Int, Long)): Unit = {}
    }

    val limitedWorker = WorkerLogic.addPullLimiter(worker, 10)

    (1 to 20).foreach(x => limitedWorker.onRecv(x, ps))
    ps.pullCounter should be (10)

    (1 to 5).foreach(x => limitedWorker.onPullRecv(x, -1, ps))
    ps.pullCounter should be (15)

    limitedWorker.onRecv(21, ps)
    ps.pullCounter should be (15)

    (6 to 21).foreach(x => limitedWorker.onPullRecv(x, -1, ps))
    ps.pullCounter should be (21)
  }

}
