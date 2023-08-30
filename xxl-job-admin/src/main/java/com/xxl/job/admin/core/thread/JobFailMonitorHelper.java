package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import com.xxl.job.admin.core.util.I18nUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * job monitor instance
 * <p>
 * 扫描下发失败的任务，重试
 *
 * @author xuxueli 2015-9-1 18:05:56
 */
public class JobFailMonitorHelper {
    private static Logger logger = LoggerFactory.getLogger(JobFailMonitorHelper.class);

    private static JobFailMonitorHelper instance = new JobFailMonitorHelper();

    public static JobFailMonitorHelper getInstance() {
        return instance;
    }

    // ---------------------- monitor ----------------------

    private Thread monitorThread;
    private volatile boolean toStop = false;

    public void start() {
        monitorThread = new Thread(() -> {
            // monitor
            while (!toStop) {
                try {
                    /**
                     * SELECT id FROM `xxl_job_log`
                     * 		WHERE !(
                     * 			(trigger_code in (0, 200) and handle_code = 0)
                     * 			OR
                     * 			(handle_code = 200)
                     * 		)
                     * 		AND `alarm_status` = 0
                     * 		ORDER BY id ASC
                     * 		LIMIT #{pagesize}
                     *
                     * 	查找执行失败的定时任务的id集合
                     */
                    List<Long> failLogIds = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().findFailJobLogIds(1000);
                    if (failLogIds != null && !failLogIds.isEmpty()) {
                        for (long failLogId : failLogIds) {

                            // lock log：-1表示先锁定这条记录，在操作他，如果其他线程在处理了，状态被锁定为-1，此时本线程执不执行本次扫描
                            int lockRet = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateAlarmStatus(failLogId, 0, -1);
                            if (lockRet < 1) {
                                // 其他调度中心线程正在执行本条失败日志的处理，此线程尝试记录锁定失败，跳过，继续执行下一条失败的日志
                                continue;
                            }
                            // 操作之前写锁定这条记录，防止并发更新
                            // 记录锁定成功，查询这条日志记录的详细信息
                            XxlJobLog log = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().load(failLogId);
                            // 查询任务具体信息
                            XxlJobInfo info = XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().loadById(log.getJobId());

                            // 1、fail retry monitor
                            /**
                             * 下发任务失败的，这里重试
                             */
                            if (log.getExecutorFailRetryCount() > 0) {
                                // 任务重试下发给远程执行器，同时，重试此时-1，如果下发失败，-1后的重试次数会更新到数据库中，下次这里就能再次读取到剩余的重试次数
                                /**
                                 * 任务下发交给线程池
                                 */
                                JobTriggerPoolHelper.trigger(log.getJobId(), TriggerTypeEnum.RETRY, (log.getExecutorFailRetryCount() - 1), log.getExecutorShardingParam(), log.getExecutorParam(), null);

                                String retryMsg = "<br><br><span style=\"color:#F39C12;\" > >>>>>>>>>>>" + I18nUtil.getString("jobconf_trigger_type_retry") + "<<<<<<<<<<< </span><br>";
                                log.setTriggerMsg(log.getTriggerMsg() + retryMsg);
                                // 更新任务重试记录信息
                                /**
                                 *  这里会不会存在：下发失败更新日志，这里又覆盖了的情况？
                                 *  理论上这里下发是异步的，真正执行下发是通过http请求同步发送，如果下发失败，也需要3s超时时间，一般情况下，这里会先更新
                                 */
                                XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateTriggerInfo(log);
                            }

                            // 2、fail alarm monitor
                            /**
                             * 下发失败的，这里需要发送告警
                             */
                            int newAlarmStatus = 0;        // 告警状态：0-默认、-1=锁定状态、1-无需告警、2-告警成功、3-告警失败
                            if (info != null) {
                                boolean alarmResult = XxlJobAdminConfig.getAdminConfig().getJobAlarmer().alarm(info, log);
                                newAlarmStatus = alarmResult ? 2 : 3;
                            } else {
                                newAlarmStatus = 1;
                            }
                            /**
                             * 根据发送结果设置下发失败任务的告警状态，只有之前状态被设为-1状态的，此时才能修改成功，保证了在多个调度中的情况下，同时只有一个线程执行扫描任务
                             */
                            XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateAlarmStatus(failLogId, -1, newAlarmStatus);
                        }
                    }

                } catch (Exception e) {
                    if (!toStop) {
                        logger.error(">>>>>>>>>>> xxl-job, job fail monitor thread error:{}", e);
                    }
                }

                try {
                    TimeUnit.SECONDS.sleep(10);
                } catch (Exception e) {
                    if (!toStop) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
            logger.info(">>>>>>>>>>> xxl-job, job fail monitor thread stop");
        });
        monitorThread.setDaemon(true);
        monitorThread.setName("xxl-job, admin JobFailMonitorHelper");
        monitorThread.start();
    }

    public void toStop() {
        toStop = true;
        // interrupt and wait
        monitorThread.interrupt();
        try {
            monitorThread.join();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

}
