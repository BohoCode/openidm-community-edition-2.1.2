/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright © 2011 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package org.forgerock.openidm.scheduler;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import javax.xml.bind.DatatypeConverter;

import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.component.ComponentContext;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.forgerock.openidm.config.EnhancedConfig;
import org.forgerock.openidm.config.JSONEnhancedConfig;
import org.forgerock.openidm.config.InvalidException;
import org.forgerock.openidm.scheduler.impl.Activator;

import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.Scheduler;

/**
 * Scheduler service using Quartz
 * @author aegloff
 */
@Component(name = "org.forgerock.scheduler", immediate=true, policy=ConfigurationPolicy.REQUIRE, configurationFactory=true)
@Service(value = SchedulerService.class, serviceFactory=false) 
@Properties({
    @Property(name = "service.description", value = "Scheduler Service using Quartz"),
    @Property(name = "service.vendor", value = "ForgeRock AS")
})
public class SchedulerService  {
    final static Logger logger = LoggerFactory.getLogger(SchedulerService.class);

    // Keys in the OSGi configuration     
    public final static String SCHEDULE_TYPE = "type";
    public final static String SCHEDULE_START_TIME = "startTime";
    public final static String SCHEDULE_END_TIME = "endTime";
    public final static String SCHEDULE_CRON_SCHEDULE = "schedule";
    public final static String SCHEDULE_TIME_ZONE = "timeZone";
    public final static String SCHEDULE_INVOKE_SERVICE = "invokeService";
    public final static String SCHEDULE_INVOKE_CONTEXT = "invokeContext";

    // Valid configuration values
    public final static String SCHEDULE_TYPE_CRON = "cron"; 
    
    // Default service PID prefix to use if the invokeService name is a fragment
    public final static String SERVICE_RDN_PREFIX = "org.forgerock.openidm.";
    
    // Internal service tracker
    final static String SERVICE_TRACKER = "scheduler.service-tracker";
    
    EnhancedConfig enhancedConfig = new JSONEnhancedConfig();
    
    // Configuration
    String scheduleType;
    Date startTime;
    Date endTime;
    String cronSchedule;
    TimeZone timeZone;
    String invokeService;
    Object invokeContext;

    // Optional user defined name for this instance, derived from the file install name
    String configFactoryPID;
    
    // Scheduling
    Scheduler scheduler;
    String jobName;
    String groupName;
    
    // Tracks OSGi services that match the configured service PID
    ServiceTracker scheduledServiceTracker;
    
    @Activate
    void activate(ComponentContext compContext) throws SchedulerException, ParseException { 
        logger.debug("Activating Service with configuration {}", compContext.getProperties());
                
        initConfig(compContext);
        scheduledServiceTracker = createServiceTracker(compContext, invokeService);

        try {
            scheduler = Activator.getScheduler();
            
            if (configFactoryPID != null) {
                jobName = configFactoryPID;
            } else {
                jobName = (String) compContext.getProperties().get(Constants.SERVICE_PID);
            }
            groupName = "scheduler-service-group";
            
            if (scheduler != null && cronSchedule != null && cronSchedule.length() > 0) {
                JobDetail job = new JobDetail(jobName, groupName, SchedulerServiceJob.class);
                JobDataMap context = new JobDataMap();
                context.put(SERVICE_TRACKER, scheduledServiceTracker);
                context.put(ScheduledService.CONFIGURED_INVOKE_SERVICE, invokeService);
                context.put(ScheduledService.CONFIGURED_INVOKE_CONTEXT, invokeContext);
                job.setJobDataMap(context);
                
                CronTrigger trigger = new CronTrigger("trigger-" + jobName, groupName, cronSchedule);
                if (startTime != null) {
                    trigger.setStartTime(startTime); // TODO: review time zone consistency with cron trigger timezone
                }
                if (endTime != null) {
                    trigger.setEndTime(endTime);
                }
                if (timeZone != null) {
                    trigger.setTimeZone(timeZone);
                }
                scheduler.scheduleJob(job, trigger);
                logger.info("Job {} scheduled with schedule {}, timezone {}, start time {}, end time {}.", 
                        new Object[] {jobName, cronSchedule, timeZone, startTime, endTime});
            }
        } catch (ParseException ex) {
            logger.warn("Parsing of scheduler configuration failed, can not create scheduler service for " 
                    + jobName + ": " + ex.getMessage(), ex);
            throw ex;
        } catch (SchedulerException ex) {
            logger.warn("Failed to create scheduler service for " + jobName + ": " + ex.getMessage(), ex);
            throw ex;
        }
    }
    
    /**
     * Initialize the service configuration
     * @throws InvalidException if the configuration is invalid.
     */
    void initConfig(ComponentContext compContext) throws InvalidException {
        
        // Optional property SERVICE_FACTORY_PID set by JSONConfigInstaller
        configFactoryPID = (String) compContext.getProperties().get("config.factory-pid");
        
        Map<String, Object> config = enhancedConfig.getConfiguration(compContext);
        logger.debug("Scheduler service activating with configuration {}", config);
        
        cronSchedule = (String) config.get(SCHEDULE_CRON_SCHEDULE);
        scheduleType = (String) config.get(SCHEDULE_TYPE);
        invokeService = (String) config.get(SCHEDULE_INVOKE_SERVICE);

        if (invokeService == null || invokeService.trim().length() == 0) {
            throw new InvalidException("Invalid scheduler configuration, the " 
                    + SCHEDULE_INVOKE_SERVICE + " property needs to be set but is empty. "
                    + "Complete config:" + config);
        } else {
            // service PIDs fragments are prefixed with openidm qualifier
            if (!invokeService.contains(".")) {
                String fragment = invokeService;
                invokeService = SERVICE_RDN_PREFIX + fragment;
                logger.info("InvokeService configured with a fragment {}, expanded to qualified {}", 
                        fragment, invokeService);
            }
        }
        
        invokeContext = config.get(SCHEDULE_INVOKE_CONTEXT);
        
        String timeZoneString = (String) config.get(SCHEDULE_TIME_ZONE);
        String startTimeString = (String) config.get(SCHEDULE_START_TIME);
        String endTimeString = (String) config.get(SCHEDULE_END_TIME);
        
        if (timeZoneString != null && timeZoneString.trim().length() > 0) {
            timeZone = TimeZone.getTimeZone(timeZoneString);
            // JDK has fall-back behavior to GMT if it doesn't understand timezone passed
            if (!timeZoneString.equals(timeZone.getID())) {
                throw new InvalidException("Scheduler configured timezone is not understood: " + timeZoneString);
            }
        }
        if (startTimeString != null && startTimeString.trim().length() > 0) {
            Calendar parsed = DatatypeConverter.parseDateTime(startTimeString);
            startTime = parsed.getTime();
            // TODO: enhanced logging for failure
        }
        
        if (endTimeString != null && endTimeString.trim().length() > 0) {
            Calendar parsed = DatatypeConverter.parseDateTime(endTimeString);
            endTime = parsed.getTime();
            // TODO: enhanced logging for failure
        }
        
        if (scheduleType != null && scheduleType.trim().length() > 0) {
            if (!scheduleType.equals(SCHEDULE_TYPE_CRON)) {
                throw new InvalidException("Scheduler configuration contains unknown schedule type " 
                        + scheduleType + ". Known types include " + SCHEDULE_TYPE_CRON);
            }
        }
    }
    
    ServiceTracker createServiceTracker(ComponentContext compContext, String servicePID) throws InvalidException {
        Filter filter = null;
        try {
            filter = FrameworkUtil.createFilter(
                    "(&(" + Constants.OBJECTCLASS + "=" + ScheduledService.class.getName() + ")" 
                    + "(service.pid=" + invokeService + "))");
        } catch (InvalidSyntaxException ex) {
            throw new InvalidException("Failure in setting up scheduler to find service to invoke. One possible cause is an invalid " 
                    + SCHEDULE_INVOKE_SERVICE + " property. :  " + ex.getMessage(), ex);
        }
        ServiceTracker serviceTracker = new ServiceTracker(compContext.getBundleContext(), filter, null);
        serviceTracker.open();
        
        return serviceTracker;
    }
    
    @Deactivate
    void deactivate(ComponentContext compContext) { 
        logger.debug("Deactivating Service {}", compContext);
        try {
            scheduler.deleteJob(jobName, groupName);
        } catch (SchedulerException ex) {
            logger.warn("Failure during removal of scheduled job ", ex);
        }
        
        if (scheduledServiceTracker != null) {
            scheduledServiceTracker.close();
        }

        logger.info("Scheduler service for {} stopped.", jobName);
    }
}