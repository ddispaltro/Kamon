package akka.kamon.instrumentation

import akka.actor.{ Cell, Props, ActorRef, ActorSystem }
import akka.dispatch.{ Envelope, MessageDispatcher }
import akka.routing.RoutedActorCell
import kamon.akka.RouterMetrics
import kamon.trace.{ Tracer, EmptyTraceContext }
import kamon.util.NanoTimestamp
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

trait RouterInstrumentation {
  def captureEnvelopeContext(): EnvelopeContext
  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef
  def processFailure(failure: Throwable): Unit
  def cleanup(): Unit

  def routeeAdded(): Unit
  def routeeRemoved(): Unit
}

object RouterInstrumentation {

  def createRouterInstrumentation(): RouterInstrumentation = ContextPropagationRouterInstrumentation
}

object NoOpRouterInstrumentation extends RouterInstrumentation {
  def captureEnvelopeContext(): EnvelopeContext = EnvelopeContext.Empty
  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef = pjp.proceed()
  def processFailure(failure: Throwable): Unit = {}
  def routeeAdded(): Unit = {}
  def routeeRemoved(): Unit = {}
  def cleanup(): Unit = {}
}

object ContextPropagationRouterInstrumentation extends RouterInstrumentation {
  def captureEnvelopeContext(): EnvelopeContext =
    EnvelopeContext(new NanoTimestamp(0L), Tracer.currentContext, None)

  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef = {
    println("=======> Envelope: " + envelopeContext)
    Tracer.withContext(envelopeContext.context)(pjp.proceed())
  }

  def processFailure(failure: Throwable): Unit = {}
  def routeeAdded(): Unit = {}
  def routeeRemoved(): Unit = {}
  def cleanup(): Unit = {}
}

/*
class RouterMetricsInstrumentation(routerMetrics: RouterMetrics) {
  private val _metricsOpt = Some(routerMetrics)

  def captureEnvelopeContext(): EnvelopeContext = {
    EnvelopeContext(NanoTimestamp.now, EmptyTraceContext, _metricsOpt)
  }

  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef = {
    val timestampBeforeProcessing = NanoTimestamp.now

    try {
      pjp.proceed()
    } finally {
      val timestampAfterProcessing = NanoTimestamp.now

      val timeInMailbox = timestampBeforeProcessing - envelopeContext.nanoTime
      val processingTime = timestampAfterProcessing - timestampBeforeProcessing

      routerMetrics.processingTime.record(processingTime.nanos)
      routerMetrics.timeInMailbox.record(timeInMailbox.nanos)

    }
  }
}
*/

@Aspect
class RoutedActorCellInstrumentation {

  def routerInstrumentation(cell: Cell): RouterInstrumentation =
    cell.asInstanceOf[RouterInstrumentationAware].routerInstrumentation

  @Pointcut("execution(akka.routing.RoutedActorCell.new(..)) && this(cell) && args(system, ref, props, dispatcher, routeeProps, supervisor)")
  def routedActorCellCreation(cell: RoutedActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, routeeProps: Props, supervisor: ActorRef): Unit = {}

  @After("routedActorCellCreation(cell, system, ref, props, dispatcher, routeeProps, supervisor)")
  def afterRoutedActorCellCreation(cell: RoutedActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, routeeProps: Props, supervisor: ActorRef): Unit = {
    cell.asInstanceOf[RouterInstrumentationAware].setRouterInstrumentation(
      RouterInstrumentation.createRouterInstrumentation())
  }

  @Pointcut("execution(* akka.routing.RoutedActorCell.sendMessage(*)) && this(cell) && args(envelope)")
  def sendMessageInRouterActorCell(cell: RoutedActorCell, envelope: Envelope) = {}

  @Around("sendMessageInRouterActorCell(cell, envelope)")
  def aroundSendMessageInRouterActorCell(pjp: ProceedingJoinPoint, cell: RoutedActorCell, envelope: Envelope): Any = {
    println("Capturing the send of: " + envelope + " in: " + cell.self)
    routerInstrumentation(cell).processMessage(pjp, envelope.asInstanceOf[InstrumentedEnvelope].envelopeContext())

    /*    val cellMetrics = cell.asInstanceOf[RoutedActorCellMetrics]
    val timestampBeforeProcessing = System.nanoTime()
    val contextAndTimestamp = envelope.asInstanceOf[TimestampedTraceContextAware]

    try {
      Tracer.withContext(contextAndTimestamp.traceContext) {

        // The router metrics recorder will only be picked up if the message is sent from a tracked router.
        RouterAwareEnvelope.dynamicRouterMetricsRecorder.withValue(cellMetrics.routerRecorder) {
          pjp.proceed()
        }
      }
    } finally {
      cellMetrics.routerRecorder.foreach { routerRecorder ⇒
        routerRecorder.routingTime.record(System.nanoTime() - timestampBeforeProcessing)
      }
    }*/
  }
}

trait RouterInstrumentationAware {
  def routerInstrumentation: RouterInstrumentation
  def setRouterInstrumentation(ai: RouterInstrumentation): Unit
}

object RouterInstrumentationAware {
  def apply(): RouterInstrumentationAware = new RouterInstrumentationAware {
    private var _ri: RouterInstrumentation = _

    def setRouterInstrumentation(ai: RouterInstrumentation): Unit = _ri = ai
    def routerInstrumentation: RouterInstrumentation = _ri
  }
}

@Aspect
class MetricsIntoRouterCellsMixin {

  @DeclareMixin("akka.routing.RoutedActorCell")
  def mixinActorCellMetricsToRoutedActorCell: RouterInstrumentationAware = RouterInstrumentationAware()

}