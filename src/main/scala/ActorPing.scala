package edu.stanford.junction.sample.tower_defense
/* 
* Copyright 2007-2009 WorldWide Conferencing, LLC 
* 
* Licensed under the Apache License, Version 2.0 (the "License"); 
* you may not use this file except in compliance with the License. 
* You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 
* Unless required by applicable law or agreed to in writing, software 
* distributed under the License is distributed on an "AS IS" BASIS, 
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
* See the License for the specific language governing permissions and 
* limitations under the License. 
*/  

import _root_.scala.actors.{Actor, Exit}  
import _root_.scala.actors.Actor._  
import _root_.java.util.concurrent._  

/** 
* The ActorPing object schedules an actor to be ping-ed with a given message at specific intervals. 
* The schedule methods return a ScheduledFuture object which can be cancelled if necessary 
*/  
object ActorPing {  
  
  /** The underlying <code>java.util.concurrent.ScheduledExecutor</code> */  
  private var service = Executors.newSingleThreadScheduledExecutor(TF)  
  
  /** 
  * Re-create the underlying <code>SingleThreadScheduledExecutor</code> 
  */  
  def restart: Unit = synchronized { if ((service eq null) || service.isShutdown)  
    service = Executors.newSingleThreadScheduledExecutor(TF) }  
  
  /** 
  * Shut down the underlying <code>SingleThreadScheduledExecutor</code> 
  */  
  def shutdown: Unit = synchronized { service.shutdown }  
  
  /** 
  * Schedules the sending of a message to occur after the specified delay. 
  * 
  * @return a <code>ScheduledFuture</code> which sends the <code>msg</code> to 
  * the <code>to<code> Actor after the specified TimeSpan <code>delay</code>. 
  */  
  def schedule(to: Actor, msg: Any, delay: Int): ScheduledFuture[AnyRef] = {  
    val r = new _root_.java.util.concurrent.Callable[AnyRef] { def call: AnyRef = { to ! msg; null } }  
    try {  
      service.schedule(r, delay, TimeUnit.MILLISECONDS)  
    } catch {  
      case e => throw ActorPingException(msg + " could not be scheduled on " + to, e)  
    }  
  }  
  
  /** 
  * Schedules the sending of the message <code>msg</code> to the <code>to<code> Actor, 
  * after <code>initialDelay</code> and then subsequently every <code>delay</code> TimeSpan. 
  */  
  def scheduleAtFixedRate(to: Actor, msg: Any, initialDelay: Int, delay: Int) {  
    try {  
      val future = service.scheduleAtFixedRate(new _root_.java.lang.Runnable {  
          def run = {  
            to ! msg;  
          }  
	}, initialDelay, delay, TimeUnit.MILLISECONDS)  
      actor {  
        self.link(to)  
        self.trapExit = true  
        to ! Scheduled  
        loop {  
          react {  
            case UnSchedule | Exit(_, _) =>  
            future cancel(true);  
            self.unlink(to)  
            exit  
          }  
        }  
      }  
    }  
    catch { case e => throw ActorPingException(msg + " could not be scheduled on " + to, e)}  
  }  
  
}  

/** 
* Send by the scheduled actor to sign off from recurrent scheduling 
*/  
case object UnSchedule  

/** 
* Send to the actor that we scheduled for recurrent ping 
*/  
case object Scheduled  

/** 
* Exception thrown if a ping can't be scheduled. 
*/  
case class ActorPingException(msg: String, e: Throwable) extends RuntimeException(msg, e)  

private object TF extends ThreadFactory {  
  val threadFactory = Executors.defaultThreadFactory()  
  def newThread(r: Runnable) : Thread = {  
    val d: Thread = threadFactory.newThread(r)  
    d setName "ActorPing"  
    d setDaemon true  
    d  
  }  
}  
