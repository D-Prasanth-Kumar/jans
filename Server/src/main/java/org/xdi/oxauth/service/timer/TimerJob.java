package org.xdi.oxauth.service.timer;

import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.xdi.oxauth.service.timer.event.TimerEvent;

public class TimerJob implements Job {

	public static final String KEY_TIMER_EVENT = TimerEvent.class.getName();
	public static final String TIMER_JOB_GROUP = "TimerJobGroup";

	@Inject
	private Logger log;

	@Inject
	private BeanManager beanManager;

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		try {
			TimerEvent timerEvent = (TimerEvent) context.getJobDetail().getJobDataMap().get(KEY_TIMER_EVENT);
			if (timerEvent == null) {
				return;
			}

			log.debug("Fire timer event [{}] with qualifiers {}", timerEvent.getTargetEvent().getClass().getName(),
					timerEvent.getQualifiers());

			beanManager.fireEvent(timerEvent.getTargetEvent(), timerEvent.getQualifiers());
		} catch (Exception ex) {
			throw new JobExecutionException(ex);
		}
	}

}
