#####################################
# NOTIFICATION BY EMAIL v6 - README #
#####################################
------------------------
Author: Vincent Couturier W4 SA (vincent.couturier@w4.eu)
------

CAUTION: This module is provided as a goodies and is not supported by the Support Services of W4 SA. 
If you want support you can contact W4 Professional Services or the person who send you this module.

This module is an extension which replaces the classical notification mechanisms.
It uses a process in order to notify a new task to its related actors.
Thanks to a mail notification step, this process sends a mail through SMTP.

In order to install this module, please follow the instruction of the INSTALL.TXT file.

The mail templates are in the extbus/product/connectors/extended/w4mail_2_0/resources/template_notif directory. 

There are two kinds of templates: subject and content. 

You can have a specific subject or content template file for a specific process or activity in a specific language.
In this case you have to create <Your Process>.html file plus <Your Process>_<LG>.html files where <LG> is the iso language code you want to support.
If there's no specific template file the notification will use the content/standard.html or content/standard_<LG>.html and the subject/standard.html or subject/standard_<LG>.html.

By default just english and french are supported.


OPTIONAL ENHANCEMENT

In order to access notified task variables or actors data you will need to use JavaMail connector "JavaMail".
After the installation of the module you can adapt your templates and get the information you want thanks to the following expressions : 

  $custom.workflowService.getTask($taskVariables.Task.value).attachedTaskVariables.MyVar.value
  $custom.workflowService.getWorkcase($taskVariables.Workcase.value).attachedWorkcaseVariables.MyVar.value
  ${custom.workflowService.getActor($taskVariables.Actors.value[0]).allAttributes.firstName
  
But you can also use a constant or the result of other operation :
  ${custom.workflowService.getTask("15498").attachedTaskVariables.ManagerComments.value}

  #(set $myTask = $custom.workflowService.getTask($taskVariables.Task.value).attachedTaskVariables.OVERDUE.value)
  ${custom.workflowService.getTask($myTask).attachedTaskVariables.ManagerComments.value}
