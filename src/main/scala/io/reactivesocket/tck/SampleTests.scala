/*
 * Copyright 2016 Facebook, Inc.
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */

package io.reactivesocket.tck

object clienttest extends RequesterDSL {
  def main(args: Array[String]) {
    RequesterReflection.runTests(this, this.writer)
  }

  @Test
  def requestResponseTest1() : Unit = {
    val s1 = requestResponse("a", "b")
    s1 request 1
    s1 awaitTerminal()
    s1 assertCompleted()
  }

  @Test
  def requestResponseTest2() : Unit = {
    val s1 = requestResponse("c", "d")
    s1 request 1
    s1 awaitTerminal()
    s1 assertReceived List(("ding", "dong"))
    s1 assertCompleted()
    s1 assertNoErrors()
  }

  @Test
  def requestResponseTest3() : Unit = {
    val s1 = requestResponse("e", "f")
    s1 request 1
    s1 awaitTerminal()
    s1 assertError()
    s1 assertNotCompleted()
  }

  // example for testing stream
  @Test
  def streamTest1() : Unit = {
    val s1 = requestStream("a", "b")
    s1 request 3
    //val s2 = requestStream("c", "d")
    s1 awaitAtLeast(3)
    // s2 request 1
    s1 assertReceived(List(("a", "b"), ("c", "d"), ("e", "f")))
    s1 request 3
    s1 awaitTerminal()
    s1 assertCompleted()
    s1 assertNoErrors()
    s1 assertReceivedCount 6
    //s2 cancel()
    //s2 assertCanceled()
    //s2 assertNoErrors()
  }

  @Test
  def streamTest2() : Unit = {
    val s2 = requestStream("c", "d")
    s2 request 2
    s2 awaitAtLeast (2)
    s2 cancel()
    s2 assertCanceled()
    s2 assertNoErrors()
  }

  @Test
  def streamTest3() : Unit = {
    val s2 = requestStream("c", "d")
    s2 request 5
    s2 awaitAtLeast (5)
    s2 awaitNoAdditionalEvents 5000
    s2 cancel()
    s2 assertCanceled()
    s2 assertNoErrors()
  }

  @Test
  def echoTest() : Unit = {
    requestChannel using("e", "f") asFollows(() => {
      respond("a")
      val cs = channelSubscriber()
      cs request(1)
      cs awaitAtLeast (1)
      cs request(10)
      respond("abcdefghijkmlnopqrstuvwxyz")
      cs awaitAtLeast (10)
      cs request(20)

    })
  }

  // example for testing channel
  @Test
  def channelTest1() : Unit = {
    requestChannel using("a", "b") asFollows(() => { // onChannelRequest
      respond("-a-")
      val s1 = channelSubscriber
      s1 request 1
      respond("-b-c-d-e-f-")
      s1 awaitAtLeast(1)
      s1 assertReceivedAtLeast 1
      s1 assertReceived List(("x", "x"))
      s1 request 2
      respond("-g-h-i-j-k-")
      s1 awaitAtLeast(4)
      s1 request 4
      s1 awaitAtLeast(7)
      respond("|")
      s1 awaitTerminal()
      s1 assertCompleted()
    })
  }

  @Test
  def channelTest2() : Unit = {
    requestChannel using("c", "d") asFollows(() => { // onChannelRequest
      respond("-a-")
      val s1 = channelSubscriber
      s1 request 1
      respond("-b-c-d-e-f-")
      s1 awaitAtLeast(1)
      s1 assertReceivedAtLeast 1
      s1 assertReceived List(("x", "x"))
      s1 request 2
      respond("-g-h-i-j-k-")
      s1 awaitAtLeast(4)
      s1 request 4
      s1 awaitAtLeast(7)
      respond("|")
      s1 awaitTerminal()
      s1 assertCompleted()
      s1 awaitNoAdditionalEvents 100
    })
  }

}

object servertest extends ResponderDSL {
  def main(args: Array[String]) {
    ResponderReflection.runTests(this, this.writer)
  }

  @Test
  def handleEcho() : Unit = {
    // not really a test... more like set up a behavior
    requestEchoChannel handle("e", "f")
  }

  @Test
  def handleRequestResponse() : Unit = {
    requestResponse handle("a", "b") using(Map("x" -> ("hello", "goodbye")), pause(3), emit('x'),
      pause(4), pause(5), complete)

    requestResponse handle("c", "d") using(Map("x" -> ("ding", "dong")), pause(10), emit('x'),
      pause(10), complete)

    requestResponse handle("e", "f") using(pause(10), error)

    requestResponse handle("g", "h") using("-")
  }

  @Test
  def handleRequestStream() : Unit = {
    requestStream handle("a", "b") using(Map("a" -> ("a", "b"), "b" -> ("c", "d"), "c" -> ("e", "f")),
      "---a-----b-----c-----d--e--f---|")
    requestStream handle("c", "d") using(Map("a" -> ("a", "b"), "b" -> ("c", "d"), "c" -> ("e", "f")),
      "---a-----b-----c-----d--e--f---|")
  }

  @Test
  def handleRequestChannel() : Unit = {
    requestChannel handle("a", "b") asFollows(() => {
      val s1 = channelSubscriber()
      respond("---x---")
      s1 request 1
      s1 awaitAtLeast(2)
      s1 assertReceivedCount 2
      s1 assertReceived List(("a", "b"), ("a", "a"))
      s1 request 5
      s1 awaitAtLeast(7)
      respond("a---b---c")
      s1 request 5
      s1 awaitAtLeast(12) // there's an implicit request 1 in the beginning
      respond("d--e---f-")
      respond("|")
      s1 awaitTerminal()
      s1 assertCompleted()
    })
  }

  @Test
  def handleRequestChannel2() : Unit = {
    requestChannel handle("c", "d") asFollows(() => {
      val s1 = channelSubscriber()
      respond("---x---")
      s1 request 1
      s1 awaitAtLeast(2)
      s1 assertReceivedCount 2
      s1 assertReceived List(("c", "d"), ("a", "a"))
      s1 request 5
      s1 awaitAtLeast(7)
      respond("a---b---c")
      s1 request 5
      s1 awaitAtLeast(12) // there's an implicit request 1 in the beginning
      respond("d--e---f-")
      respond("|")
      s1 awaitTerminal()
      s1 assertCompleted()
      s1 awaitNoAdditionalEvents 1000
    })
  }

}
