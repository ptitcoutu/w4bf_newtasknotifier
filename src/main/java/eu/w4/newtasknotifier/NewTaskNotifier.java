package eu.w4.newtasknotifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.w4.bpm.BPMDataType;
import eu.w4.bpm.BPMException;
import eu.w4.bpm.BPMId;
import eu.w4.bpm.BPMInternalId;
import eu.w4.bpm.BPMLogicalId;
import eu.w4.bpm.BPMVariable;
import eu.w4.bpm.BPMVariableMap;
import eu.w4.bpm.BPMVariables;
import eu.w4.bpm.search.BPMCastFilter;
import eu.w4.bpm.search.BPMCastSort;
import eu.w4.bpm.search.BPMCastSortBy;
import eu.w4.bpm.search.BPMSortMode;
import eu.w4.bpm.search.BPMTaskAttachment;
import eu.w4.bpm.service.BPMActorService;
import eu.w4.bpm.service.BPMActorSnapshot;
import eu.w4.bpm.service.BPMCastSnapshot;
import eu.w4.bpm.service.BPMDomainService;
import eu.w4.bpm.service.BPMDomainSnapshot;
import eu.w4.bpm.service.BPMIncompleteSnapshotException;
import eu.w4.bpm.service.BPMProcessService;
import eu.w4.bpm.service.BPMService;
import eu.w4.bpm.service.BPMSessionId;
import eu.w4.bpm.service.BPMSessionService;
import eu.w4.bpm.service.BPMTaskService;
import eu.w4.bpm.service.BPMTaskSnapshot;
import eu.w4.bpm.service.BPMWorkcaseService;
import eu.w4.bpm.service.BPMWorkcaseSnapshot;
import eu.w4.bpm.w4suite.service.WFSessionKey;
import eu.w4.connector.W4ConnectorContext;
import eu.w4.connector.W4ConnectorException;
import eu.w4.connector.W4Listener;
import eu.w4.connector.W4Main;
import eu.w4.connector.W4Message;
import eu.w4.util.log.Log;
import eu.w4.util.log.LogFactory;

import fr.w4.TWFexception;
import fr.w4.buildtime.dynamic.TWFnode;
import fr.w4.buildtime.ref.TWFnodeRef;
import fr.w4.http.TWFhttpContext;
import fr.w4.session.TWFnativeSession;
import fr.w4.session.TWFsession;
import fr.w4.session.TWFsessionContext;

public class NewTaskNotifier implements W4Listener
{
  private BPMService _bpmService = null;

  private BPMTaskService _taskService = null;

  private BPMWorkcaseService _workcaseService = null;

  private BPMProcessService _processService = null;

  private BPMDomainService _domainService = null;

  private BPMActorService _actorService = null;

  private BPMSessionService _sessionService = null;

  private ExecutorService _executorService = null;

  private W4ConnectorContext _w4Context = null;

  static private Log log = LogFactory.getLog(NewTaskNotifier.class.getPackage().getName());

  private W4Main _w4Main = null;

  public void dispose(final W4Main w4Main)
  {
    // NOTHING HERE BECAUSEOF NO SPECIFIC RESOURCE
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
      _taskService = _bpmService.getTaskService();
      _domainService = _bpmService.getDomainService();
      _actorService = _bpmService.getActorService();
      _processService = _bpmService.getProcessService();
      _workcaseService = _bpmService.getWorkcaseService();
      return true;
    }
    catch (final BPMException e)
    {
      log.error(e);
      return false;
    }

  }

  public void messageReceived(final W4Message w4Message)
    throws W4ConnectorException
  {
    // Reject empty messages
    if (w4Message == null)
    {
      return;
    }
    // Reject connector messages
    if (w4Message.getConnectorName() != null)
    {
      return;
    }

    // The other task are potential user task with notification
    // the message is send to the executor to be processed
    _executorService.execute(new Runnable()
    {

      public void run()
      {
        final int taskId = w4Message.getTaskId();
        if(taskId <0) {
        	log.debug("rejected negative task id  : " + taskId);
        	return;
        } else {
        	log.info(" process notify task id  : " + taskId);
        }
        BPMSessionId sessionId = null;

        try
        {
          sessionId = _sessionService.openSession("system", "bypass".getBytes());

          final BPMTaskAttachment taskAttachment = _taskService
            .createTaskAttachment();
          taskAttachment.attachWorkcaseVariable("__leon.application.name");
          final BPMTaskSnapshot task = _taskService.getTask(sessionId, new BPMInternalId(taskId), taskAttachment);
          // ////
          TWFsession twfSession = null;
          try
          {
            final TWFsessionContext sessionContext = new TWFhttpContext(_w4Context.getSoftwareHome(),
              _w4Context.getInstanceName(), new Integer(sessionId.getParameters().get(WFSessionKey.ACTOR_ID)),
              new Integer(sessionId.getParameters().get(WFSessionKey.LOGIN_ID)));
            twfSession = new TWFnativeSession(sessionContext);
            twfSession.openConnection();
            /*
             * twfSession.setPriority(100); twfSession.login("system", null);
             */
            final TWFnodeRef nodeRef = new TWFnodeRef(new Integer(task.getStepId().getInternalId()));
            final TWFnode node = nodeRef.wfGetNode();
            final String cmdLine = node.getCommand_line();
            if (cmdLine == null || cmdLine.indexOf("://mail") == -1)
            {
              return;
            }

          }
          catch (final TWFexception e)
          {
            log.error("error while trying to get node information", e);
            return;
          }
          finally
          {
            try
            {
              twfSession.closeConnection();
            }
            catch (final TWFexception e)
            {
              log.error("error while trying to close twf connection", e);
            }
          }
          // /////

          final BPMVariableMap notifProcVars = BPMVariables.createVariableMap();
          final List<String> allActors = getPotentialTaskActors(sessionId, task);
          String subjectCategory = "subject";
          String contentCategory = "content";
          if(allActors.isEmpty()) 
          {
        	  log.warning("No actor found for task "+task.getId().getInternalId()+". Notification will be sent to responsible"); 
        	  allActors.addAll(getWorkcaseResponsible(sessionId, task));
        	  subjectCategory = "noActorSubject";
        	  contentCategory = "noActorContent";
          } 
          if(allActors.isEmpty())
          {
        	  log.warning("No responsible found for task "+task.getId().getInternalId()+". Notification will be sent to w4adm");
        	  allActors.add("w4adm");
          }

          notifProcVars.put(BPMVariables.createVariable("Actors",
                                                        BPMDataType.STRING_LIST, allActors));
          final String app = getApplication(sessionId, taskAttachment, task);
          notifProcVars.put(BPMVariables.createVariable("Application",
                                                        BPMDataType.STRING, app));

          notifProcVars.put(BPMVariables.createVariable("__leon.application.name",
                                                        BPMDataType.STRING, app));

          final String subjectTemplate = getTemplate(subjectCategory,
                                                     task.getProcessId().getLogicalId(),
                                                     task.getActivityId().getLogicalId());
          final String contentTemplate = getTemplate(contentCategory,
                                                     task.getProcessId().getLogicalId(),
                                                     task.getActivityId().getLogicalId());
          notifProcVars.put(BPMVariables.createVariable("SubjectTemplate",
                                                        BPMDataType.STRING, subjectTemplate));
          notifProcVars.put(BPMVariables.createVariable("ContentTemplate",
                                                        BPMDataType.STRING, contentTemplate));

          notifProcVars.put(BPMVariables.createVariable("Task",
                                                        BPMDataType.STRING, task.getId().getInternalId()));
          notifProcVars.put(BPMVariables.createVariable("Workcase",
                                                        BPMDataType.STRING, task.getWorkcaseId().getInternalId()));
          notifProcVars.put(BPMVariables.createVariable("Procedure",
                                                        BPMDataType.STRING, task.getProcessId().getLogicalId()));
          notifProcVars.put(BPMVariables.createVariable("Step",
                                                        BPMDataType.STRING, task.getStepId().getLogicalId()));
          notifProcVars.put(BPMVariables.createVariable("Activity",
                                                        BPMDataType.STRING, task.getActivityId().getLogicalId()));

          final BPMWorkcaseSnapshot notificationWorkcase = _processService.createWorkcase(sessionId,
                                                                                          new BPMLogicalId(
                                                                                            "EMAIL_NOTIFICATION"));
          _workcaseService.start(sessionId, notificationWorkcase.getId(), notifProcVars);
        }
        catch (final BPMException e)
        {
          log.error("error while trying to start process", e);
        }
        finally
        {
          try
          {
            _sessionService.closeSession(sessionId);
          }
          catch (final Exception e)
          {
            log.error("error while trying to close bpm connection", e);
          }
        }

      }

      private String getApplication(final BPMSessionId sessionId,
                                    final BPMTaskAttachment taskAttachment,
                                    final BPMTaskSnapshot initialTask) throws BPMIncompleteSnapshotException,
        BPMException
      {
        BPMTaskSnapshot task = initialTask;

        String app = "";

        while ((app == null || app.trim().length() == 0) && task != null)
        {
          final BPMVariable appVar = task.getAttachedWorkcaseVariables().get(
                                                                             "__leon.application.name");
          if (appVar != null)
          {
            app = appVar.getValue();
          }
          if (app == null || app.trim().length() == 0)
          {
            final BPMWorkcaseSnapshot wks = _workcaseService.getWorkcase(
                                                                         sessionId, task.getWorkcaseId());
            final BPMId parentTaskId = wks.getParentTaskId();
            if (parentTaskId == null)
            {
              task = null;
            }
            else
            {
              task = _taskService.getTask(sessionId, parentTaskId, taskAttachment);
            }
          }

        }
        return app;
      }

      private List<String> getPotentialTaskActors(final BPMSessionId sessionId,
                                                  final BPMTaskSnapshot task) throws BPMException
      {
        final List<String> allActors = new ArrayList<String>();
        final BPMId domainId = task.getDomainId();
        final BPMId roleId = task.getRoleId();
        final BPMId actorId = task.getActorId();
        if (roleId == null)
        {
          final String actorLogin = actorId.getLogicalId();
          allActors.add(actorLogin);
        }
        else
        {
          final BPMCastFilter mainCastFilter = _actorService.createCastFilter();
          final ArrayList<BPMCastFilter> castFilters = new ArrayList<BPMCastFilter>();
          BPMId parentDomainId = domainId;
          while (!"global".equals(parentDomainId.getLogicalId()))
          {
            final BPMDomainSnapshot parentDomain = _domainService.getDomain(
                                                                            sessionId, parentDomainId);
            parentDomainId = parentDomain.getUpperDomainId();
            final BPMCastFilter castFilter = _actorService.createCastFilter();
            castFilter.roleIs(roleId);
            castFilter.domainIs(parentDomainId);
            castFilters.add(castFilter);
          }
          if (castFilters.size() > 0)
          {
            final BPMCastFilter castFilter = _actorService.createCastFilter();
            castFilter.roleIs(roleId);
            castFilter.domainIs(domainId);
            castFilters.add(castFilter);

            final BPMCastFilter[] castFiltersArray = castFilters
              .toArray(new BPMCastFilter[castFilters.size()]);
            mainCastFilter.or(castFiltersArray);
          }
          else
          {
            mainCastFilter.domainIs(domainId);
            mainCastFilter.roleIs(roleId);
          }
          final BPMCastSort castSort = _actorService.createCastSort(
                                                                    BPMCastSortBy.ACTOR_NAME, BPMSortMode.ASC);
          final List<BPMCastSort> castSorts = new ArrayList<BPMCastSort>(1);
          castSorts.add(castSort);
          final List<BPMCastSnapshot> casts = _actorService.searchCasts(
                                                                        sessionId, mainCastFilter, castSorts);
          for (BPMCastSnapshot cast : casts)
          {
            final BPMActorSnapshot actor = cast.getActor();
            final String actorLogin = actor.getId().getLogicalId();
            allActors.add(actorLogin);
          }
        }
        return allActors;
      }

      private List<String> getWorkcaseResponsible(final BPMSessionId sessionId,
	              final BPMTaskSnapshot task) throws BPMException
      {
		final List<String> allResponsible = new ArrayList<String>();
		final BPMId domainId = task.getDomainId();
		final BPMId workcaseId = task.getWorkcaseId();
		final BPMWorkcaseSnapshot workcase = _workcaseService.getWorkcase(sessionId, workcaseId);
		final BPMId responsibleRoleId = workcase.getResponsibleRoleId();
		final BPMId responsibleId = workcase.getResponsibleId();
		if(responsibleId != null) 
		{
			allResponsible.add(responsibleId.getLogicalId());
		}
		if (responsibleRoleId != null)
		{
			final BPMCastFilter mainCastFilter = _actorService.createCastFilter();
			final ArrayList<BPMCastFilter> castFilters = new ArrayList<BPMCastFilter>();
			BPMId parentDomainId = domainId;
			while (!"global".equals(parentDomainId.getLogicalId()))
			{
				final BPMDomainSnapshot parentDomain = _domainService.getDomain(
	                                        sessionId, parentDomainId);
				parentDomainId = parentDomain.getUpperDomainId();
				final BPMCastFilter castFilter = _actorService.createCastFilter();
				castFilter.roleIs(responsibleRoleId);
				castFilter.domainIs(parentDomainId);
				castFilters.add(castFilter);
			}
			if (castFilters.size() > 0)
			{
				final BPMCastFilter castFilter = _actorService.createCastFilter();
				castFilter.roleIs(responsibleRoleId);
				castFilter.domainIs(domainId);
				castFilters.add(castFilter);
	
				final BPMCastFilter[] castFiltersArray = castFilters
						.toArray(new BPMCastFilter[castFilters.size()]);
				mainCastFilter.or(castFiltersArray);
			}
			else
			{
				mainCastFilter.domainIs(domainId);
				mainCastFilter.roleIs(responsibleRoleId);
			}
			final BPMCastSort castSort = _actorService.createCastSort(
	                                BPMCastSortBy.ACTOR_NAME, BPMSortMode.ASC);
			final List<BPMCastSort> castSorts = new ArrayList<BPMCastSort>(1);
			castSorts.add(castSort);
			final List<BPMCastSnapshot> casts = _actorService.searchCasts(
					sessionId, mainCastFilter, castSorts);
			for (BPMCastSnapshot cast : casts)
			{
				final BPMActorSnapshot actor = cast.getActor();
				final String actorLogin = actor.getId().getLogicalId();
				allResponsible.add(actorLogin);
			}
		}
		return allResponsible;
      }      
      
      
      public String getTemplate(final String category, final String procedure,
                                final String activity)
      {
        final String templatesPath = "template_notif/"
                                     + category;
        final File templatesDir = new File(
          _w4Context.getSoftwareHome()
            + "/extbus/product/connectors/extended/w4mail_2_0/resources/" + templatesPath);
        String specificTemplateName = null;
        File specificTemplateFile = null;

        specificTemplateName = activity + ".html";
        specificTemplateFile = new File(templatesDir, specificTemplateName);
        if (specificTemplateFile.exists())
        {
          return templatesPath + "/" + specificTemplateName;
        }

        specificTemplateName = procedure + ".html";
        specificTemplateFile = new File(templatesDir, specificTemplateName);
        if (specificTemplateFile.exists())
        {
          return templatesPath + "/" + specificTemplateName;
        }

        return templatesPath + "/standard.html";
      }

    });
  }
}
