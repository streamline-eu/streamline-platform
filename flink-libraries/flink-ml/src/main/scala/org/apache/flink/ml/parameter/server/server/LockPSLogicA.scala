package org.apache.flink.ml.parameter.server.server

import org.apache.flink.ml.parameter.server.{ParameterServer, ParameterServerLogic}

import scala.collection.mutable

/**
  * Simple lock logic:
  *  A lock is triggered by each pull and hold still push is not arrived.
  *  Until lock is active a queue is dedicated to store the request.
  *  If update is arrived lock will release or if queue is not empty answer the first and keep the lock active.
  */
class LockPSLogicA[P](init: Int => P, update: (P, P) => P)
  extends ParameterServerLogic[P, (Int, P)] {

  val params = new mutable.HashMap[Int, (Boolean, P, mutable.Queue[Int])]()

  override def onPullRecv(id: Int,
                          workerPartitionIndex: Int,
                          ps: ParameterServer[P, (Int, P)]): Unit = {
    val value = params.getOrElseUpdate(id, (false, init(id), new mutable.Queue[Int]()))
    value match {
      case (false, p, q) =>
        ps.answerPull(id, p, workerPartitionIndex)
        params += ((id, (true, p, q)))
      case (true, p, q) =>
        q += workerPartitionIndex
    }
  }

  override def onPushRecv(id: Int, deltaUpdate: P, ps: ParameterServer[P, (Int, P)]): Unit = {
    params.get(id) match {
      case Some((isLocked, param, pullQueue)) =>
        val c = update(param, deltaUpdate)
        if (pullQueue.isEmpty) {
          params += ((id, (false, c, pullQueue)))
        } else {
          ps.answerPull(id, c, pullQueue.dequeue)
          params += ((id, (true, c, pullQueue)))
        }
        ps.output((id, c))
      case None =>
        throw new IllegalStateException("Not existed model was not able to update by any delta.")
    }
  }
}
