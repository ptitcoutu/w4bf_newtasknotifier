package eu.w4.newtasknotifier;

import eu.w4.bpm.*;
import eu.w4.bpm.service.*;
import eu.w4.connector.*;
import eu.w4.util.log.Log;
import eu.w4.util.log.LogFactory;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AllNewTaskNotifier implements W4Listener
{
  public static final String NOTIFICATION_PROCEDURE_NAME = "ALLNEWTASK_NOTIFICATION";

  private BPMService _bpmService = null;

  private BPMWorkcaseService _workcaseService = null;

  private BPMTaskService _taskService = null;

  private BPMProcessService _processService = null;

  private BPMSessionService _sessionService = null;

  private ExecutorService _executorService = null;

  private W4ConnectorContext _w4Context = null;

  static private Log log = LogFactory.getLog(AllNewTaskNotifier.class.getPackage().getName());

  private W4Main _w4Main = null;

  public void dispose(final W4Main w4Main)
  {
    // NOTHING HERE BECAUSE OF NO SPECIFIC RESOURCE
  }

  public boolean init(final W4Main w4Main) throws W4ConnectorException
  {
    _w4Main = w4Main;

    _bpmService = w4Main.getBPMService();
    _executorService = Executors.newCachedThreadPool();
    _w4Context = W4ConnectorContext.getConnectorContext();
    log.info("   start folder : " + new File(".").getAbsolutePath());
    try
    {
      _sessionService = _bpmService.getSessionService();
      _processService = _bpmService.getProcessService();
      _workcaseService = _bpmService.getWorkcaseService();
      _taskService = _bpmService.getTaskService();
      return true;
    }
    catch (final BPMException e)
    {
      log.error(e);
      return false;
    }

  }

  public void messageReceived(final W4Message w4Message)
    throws W4ConnectorException {
    // Reject empty messages
    if (w4Message == null) {
      return;
    }

    // the message is send to the executor to be processed
    _executorService.execute(new Runnable() {

      public void run() {
        final int taskId = w4Message.getTaskId();

        if (taskId < 0) {
          log.debug("rejected negative task id  : " + taskId);
          return;
        }

        BPMSessionId sessionId = null;

        try {
          sessionId = _sessionService.openSession("system", "bypass".getBytes());

          BPMTaskSnapshot task = _taskService.getTask(sessionId, new BPMInternalId(taskId));
          String workcaseName = task.getWorkcaseId().getLogicalId();
          if(workcaseName.startsWith(NOTIFICATION_PROCEDURE_NAME)) {
            log.debug("skip notification of notification task "+taskId+" in workcase "+workcaseName);
            return;
          }
          log.info("notify task "+taskId+" on instance "+workcaseName);
          final BPMVariableMap notifProcVars = BPMVariables.createVariableMap();

          notifProcVars.put(BPMVariables.createVariable("TaskId",
            BPMDataType.STRING, "" + taskId));

          final BPMWorkcaseSnapshot notificationWorkcase = _processService.createWorkcase(sessionId,
            new BPMLogicalId(
              NOTIFICATION_PROCEDURE_NAME));
          _workcaseService.start(sessionId, notificationWorkcase.getId(), notifProcVars);
        } catch (final BPMException e) {
          log.error("error while trying to start process", e);
        } finally {
          try {
            _sessionService.closeSession(sessionId);
          } catch (final Exception e) {
            log.error("error while trying to close bpm connection", e);
          }
        }
      }
    });
  }

}
