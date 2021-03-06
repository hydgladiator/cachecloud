package com.sohu.cache.stats.instance.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sohu.cache.alert.bean.AlertConfigBaseData;
import com.sohu.cache.alert.strategy.AlertConfigStrategy;
import com.sohu.cache.alert.strategy.AofCurrentSizeAlertStrategy;
import com.sohu.cache.alert.strategy.ClientBiggestInputBufAlertStrategy;
import com.sohu.cache.alert.strategy.ClientLongestOutputListAlertStrategy;
import com.sohu.cache.alert.strategy.ClusterSlotsOkAlertStrategy;
import com.sohu.cache.alert.strategy.ClusterStateAlertStrategy;
import com.sohu.cache.alert.strategy.InstantaneousOpsPerSecAlertStrategy;
import com.sohu.cache.alert.strategy.LatestForkUsecAlertStrategy;
import com.sohu.cache.alert.strategy.MasterSlaveOffsetAlertStrategy;
import com.sohu.cache.alert.strategy.MemFragmentationRatioAlertStrategy;
import com.sohu.cache.alert.strategy.MinuteAofDelayedFsyncAlertStrategy;
import com.sohu.cache.alert.strategy.MinuteSyncFullAlertStrategy;
import com.sohu.cache.alert.strategy.MinuteSyncPartialErrAlertStrategy;
import com.sohu.cache.alert.strategy.MinuteSyncPartialOkAlertStrategy;
import com.sohu.cache.alert.strategy.MinuteTotalNetInputMBytesAlertStrategy;
import com.sohu.cache.alert.strategy.MinuteTotalNetOutputMBytesAlertStrategy;
import com.sohu.cache.alert.strategy.RdbLastBgsaveStatusAlertStrategy;
import com.sohu.cache.dao.AppDao;
import com.sohu.cache.dao.InstanceAlertConfigDao;
import com.sohu.cache.dao.InstanceDao;
import com.sohu.cache.dao.InstanceStatsDao;
import com.sohu.cache.entity.AppDesc;
import com.sohu.cache.entity.InstanceAlertConfig;
import com.sohu.cache.entity.InstanceAlertValueResult;
import com.sohu.cache.entity.InstanceInfo;
import com.sohu.cache.entity.StandardStats;
import com.sohu.cache.redis.enums.InstanceAlertTypeEnum;
import com.sohu.cache.redis.enums.RedisAlertConfigEnum;
import com.sohu.cache.stats.instance.InstanceAlertConfigService;
import com.sohu.cache.util.ConstUtils;
import com.sohu.cache.web.component.EmailComponent;
import com.sohu.cache.web.util.VelocityUtils;

/**
 * @author leifu
 * @Date 2017???5???19???
 * @Time ??????2:16:36
 */
public class InstanceAlertConfigServiceImpl implements InstanceAlertConfigService {

    private Logger logger = LoggerFactory.getLogger(InstanceAlertConfigServiceImpl.class);

    private InstanceAlertConfigDao instanceAlertConfigDao;

    private InstanceStatsDao instanceStatsDao;

    private InstanceDao instanceDao;

    private EmailComponent emailComponent;
    
    private VelocityEngine velocityEngine;
    
    private AppDao appDao;

    private static Map<RedisAlertConfigEnum, AlertConfigStrategy> alertConfigStrategyMap = new HashMap<RedisAlertConfigEnum, AlertConfigStrategy>();
    static {
        alertConfigStrategyMap.put(RedisAlertConfigEnum.aof_current_size, new AofCurrentSizeAlertStrategy());
        alertConfigStrategyMap.put(RedisAlertConfigEnum.client_biggest_input_buf, new ClientBiggestInputBufAlertStrategy());
        alertConfigStrategyMap.put(RedisAlertConfigEnum.client_longest_output_list, new ClientLongestOutputListAlertStrategy());
        alertConfigStrategyMap.put(RedisAlertConfigEnum.instantaneous_ops_per_sec, new InstantaneousOpsPerSecAlertStrategy()); 
        alertConfigStrategyMap.put(RedisAlertConfigEnum.latest_fork_usec, new LatestForkUsecAlertStrategy()); 
        alertConfigStrategyMap.put(RedisAlertConfigEnum.mem_fragmentation_ratio, new MemFragmentationRatioAlertStrategy()); 
        alertConfigStrategyMap.put(RedisAlertConfigEnum.rdb_last_bgsave_status, new RdbLastBgsaveStatusAlertStrategy()); 
        alertConfigStrategyMap.put(RedisAlertConfigEnum.minute_aof_delayed_fsync, new MinuteAofDelayedFsyncAlertStrategy());
        alertConfigStrategyMap.put(RedisAlertConfigEnum.minute_rejected_connections, new RdbLastBgsaveStatusAlertStrategy());
        alertConfigStrategyMap.put(RedisAlertConfigEnum.minute_sync_partial_err, new MinuteSyncPartialErrAlertStrategy());
        alertConfigStrategyMap.put(RedisAlertConfigEnum.minute_sync_partial_ok, new MinuteSyncPartialOkAlertStrategy());
        alertConfigStrategyMap.put(RedisAlertConfigEnum.minute_sync_full, new MinuteSyncFullAlertStrategy());
        alertConfigStrategyMap.put(RedisAlertConfigEnum.minute_total_net_input_bytes, new MinuteTotalNetInputMBytesAlertStrategy());
        alertConfigStrategyMap.put(RedisAlertConfigEnum.minute_total_net_output_bytes, new MinuteTotalNetOutputMBytesAlertStrategy());
        alertConfigStrategyMap.put(RedisAlertConfigEnum.master_slave_offset_diff, new MasterSlaveOffsetAlertStrategy());
        alertConfigStrategyMap.put(RedisAlertConfigEnum.cluster_state, new ClusterStateAlertStrategy());
        alertConfigStrategyMap.put(RedisAlertConfigEnum.cluster_slots_ok, new ClusterSlotsOkAlertStrategy());
    }

    @Override
    public List<InstanceAlertConfig> getAll() {
        try {
            return instanceAlertConfigDao.getAll();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public int save(InstanceAlertConfig instanceAlertConfig) {
        try {
            return instanceAlertConfigDao.save(instanceAlertConfig);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return -1;
        }
    }

    @Override
    public InstanceAlertConfig get(int id) {
        try {
            return instanceAlertConfigDao.get(id);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    @Override
    public int remove(int id) {
        try {
            return instanceAlertConfigDao.remove(id);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return -1;
        }
    }

    @Override
    public List<InstanceAlertConfig> getByType(int type) {
        try {
            return instanceAlertConfigDao.getByType(type);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void update(long id, String alertValue, int checkCycle) {
        try {
            instanceAlertConfigDao.update(id, alertValue, checkCycle);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void updateLastCheckTime(long id, Date lastCheckTime) {
        try {
            instanceAlertConfigDao.updateLastCheckTime(id, lastCheckTime);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void monitorLastMinuteAllInstanceInfo() {
        long startTime = System.currentTimeMillis();
        // 1.?????????????????????????????????
        List<InstanceAlertConfig> commonInstanceAlertConfigList = getByType(InstanceAlertTypeEnum.ALL_ALERT.getValue());
        List<InstanceAlertConfig> specialInstanceAlertConfigList = getByType(InstanceAlertTypeEnum.INSTANCE_ALERT.getValue());
        List<InstanceAlertConfig> allInstanceAlertConfigList = new ArrayList<InstanceAlertConfig>();
        allInstanceAlertConfigList.addAll(commonInstanceAlertConfigList);
        allInstanceAlertConfigList.addAll(specialInstanceAlertConfigList);
        if (CollectionUtils.isEmpty(allInstanceAlertConfigList)) {
            return;
        }
        // 2.??????????????????
        List<InstanceInfo> allInstanceInfoList = instanceDao.getAllInsts();
        if (CollectionUtils.isEmpty(allInstanceInfoList)) {
            return;
        }
        // 3. ??????1??????Redis??????????????????
        Date currentTime = new Date();
        Date beginTime = DateUtils.addMinutes(currentTime, -2);
        Date endTime = DateUtils.addMinutes(currentTime, -1);
        Map<String, StandardStats> standardStatMap = getStandardStatsMap(beginTime, endTime);
        if (MapUtils.isEmpty(standardStatMap)) {
            logger.warn("standardStatMap is empty!");
            return;
        }
        
        // 4.??????????????????
        List<InstanceAlertValueResult> instanceAlertValueResultList = new ArrayList<InstanceAlertValueResult>();
        
        for (InstanceAlertConfig instanceAlertConfig : allInstanceAlertConfigList) {
            if (!checkInCycle(instanceAlertConfig)) {
                continue;
            }
            List<InstanceInfo> tempInstanceInfoList = allInstanceInfoList;
            if (instanceAlertConfig.isSpecail()) {
                tempInstanceInfoList.clear();
                InstanceInfo instanceInfo = instanceDao.getInstanceInfoById(instanceAlertConfig.getInstanceId());
                if (instanceInfo == null) {
                    continue;
                }
                tempInstanceInfoList.add(instanceInfo);
            }
            for (InstanceInfo instanceInfo : tempInstanceInfoList) {
                List<InstanceAlertValueResult> InstanceAlertValueResultTempList = dealInstanceAlert(specialInstanceAlertConfigList, instanceAlertConfig, instanceInfo, standardStatMap, currentTime);
                if (CollectionUtils.isNotEmpty(InstanceAlertValueResultTempList)) {
                    instanceAlertValueResultList.addAll(InstanceAlertValueResultTempList);
                }
            }
            // ??????????????????????????????
            updateLastCheckTime(instanceAlertConfig.getId(), currentTime);
        }
        if (CollectionUtils.isNotEmpty(instanceAlertValueResultList)) {
            // ????????????
            sendInstanceAlertEmail(beginTime, endTime, instanceAlertValueResultList);
        }
        long costTime = System.currentTimeMillis() - startTime;
        if (costTime > 20000) {
            logger.warn("monitorLastMinuteAllInstanceInfo cost {} ms", costTime);
        }
    }
    
    /**
     * ????????????
     * @param instanceAlertConfig
     * @param instanceInfo
     * @param standardStatMap
     * @param currentTime
     */
    private List<InstanceAlertValueResult> dealInstanceAlert(List<InstanceAlertConfig> specialInstanceAlertConfigList, InstanceAlertConfig instanceAlertConfig, InstanceInfo instanceInfo, Map<String, StandardStats> standardStatMap, Date currentTime) {
        if (instanceInfo.isOffline()) {
            return null;
        }
        // ???????????????????????????
        String hostPort = instanceInfo.getHostPort();
        StandardStats standardStats = standardStatMap.get(hostPort);
        if (standardStats == null) {
            return null;
        }
        // ???????????????????????????
        InstanceAlertConfig finalInstanceConfig = filterSpecial(specialInstanceAlertConfigList, instanceAlertConfig, instanceInfo);
        // ??????????????????finalInstanceConfig?????????instanceAlertConfig?????????
        if (!instanceAlertConfig.isSpecail() && finalInstanceConfig.getId() != instanceAlertConfig.getId()) {
            return null;
        }
        // ????????????????????????
        boolean isInCycle = checkInCycle(finalInstanceConfig);
        if (!isInCycle) {
            return null;
        }
        // ????????????
        String alertConfig = finalInstanceConfig.getAlertConfig();
        RedisAlertConfigEnum redisAlertConfigEnum = RedisAlertConfigEnum.getRedisAlertConfig(alertConfig);
        if (redisAlertConfigEnum == null) {
            logger.warn("alertConfig {} is not in RedisAlertConfigEnum", alertConfig);
            return null;
        }
        // ????????????
        AlertConfigStrategy alertConfigStrategy = alertConfigStrategyMap.get(redisAlertConfigEnum);
        if (alertConfigStrategy == null) {
            return null;
        }
        
        // ??????????????????
        AlertConfigBaseData alertConfigBaseData = new AlertConfigBaseData();
        alertConfigBaseData.setInstanceInfo(instanceInfo);
        alertConfigBaseData.setStandardStats(standardStats);
        // ????????????
        try {
            return alertConfigStrategy.checkConfig(finalInstanceConfig, alertConfigBaseData);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * ????????????
     * 
     * @param instanceAlertValueResultList
     */
    private void sendInstanceAlertEmail(Date beginTime, Date endTime,
            List<InstanceAlertValueResult> instanceAlertValueResultList) {
        if (CollectionUtils.isEmpty(instanceAlertValueResultList)) {
            return;
        }
        Collections.sort(instanceAlertValueResultList, new Comparator<InstanceAlertValueResult>() {

            @Override
            public int compare(InstanceAlertValueResult o1, InstanceAlertValueResult o2) {
                return (int) (o1.getAppId() - o2.getAppId());
            }
        });
        Map<Long, AppDesc> appDescMap = new HashMap<Long, AppDesc>();
        for (InstanceAlertValueResult instanceAlertValueResult : instanceAlertValueResultList) {
            long appId = instanceAlertValueResult.getAppId();
            AppDesc appDesc = null;
            if (appDescMap.containsKey(appId)) {
                appDesc = appDescMap.get(appId);
            } else {
                appDesc = appDao.getAppDescById(instanceAlertValueResult.getAppId());
                appDescMap.put(appId, appDesc);
            }
            instanceAlertValueResult.setAppDesc(appDesc);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String emailTitle = String.format("Redis??????????????????(%s~%s)", sdf.format(beginTime), sdf.format(endTime));
        String emailContent = VelocityUtils.createText(velocityEngine, null, null, null, instanceAlertValueResultList,
                "instanceAlert.vm", "UTF-8");
        emailComponent.sendMailToAdmin(emailTitle, emailContent.toString());
    }

    /**
     * ????????????????????????
     * 
     * @param finalInstanceConfig
     * @return
     */
    private boolean checkInCycle(InstanceAlertConfig finalInstanceConfig) {
        if (ConstUtils.IS_DEBUG) {
            return true;
        }
        // ???????????????????????????
        long checkCycleMillionTime = finalInstanceConfig.getCheckCycleMillionTime();
        // ??????????????????????????????????????????
        long betweenTime = new Date().getTime() - finalInstanceConfig.getLastCheckTime().getTime();
        // ???????????????????????????????????????
        if (betweenTime >= checkCycleMillionTime) {
            return true;
        }
        return false;
    }

    /**
     * ????????????????????????????????????????????????
     * @param specialInstanceAlertConfigList
     * @param instanceAlertConfig
     * @param instanceInfo
     * @return
     */
    private InstanceAlertConfig filterSpecial(List<InstanceAlertConfig> specialInstanceAlertConfigList, InstanceAlertConfig instanceAlertConfig, InstanceInfo instanceInfo) {
        // ????????????????????????????????????
        if (CollectionUtils.isEmpty(specialInstanceAlertConfigList)) {
            return instanceAlertConfig;
        }
        // ?????????????????? 
        for (InstanceAlertConfig specialInstanceAlertConfig : specialInstanceAlertConfigList) {
            String specialAlertConfig = specialInstanceAlertConfig.getAlertConfig();
            long instanceId = specialInstanceAlertConfig.getInstanceId();
            // ??????????????????id??????
            if (instanceAlertConfig.getAlertConfig().equals(specialAlertConfig) && instanceInfo.getId() == instanceId) {
                return specialInstanceAlertConfig;
            }
        }
        return instanceAlertConfig;
    }

    /**
     * ??????????????????????????????????????????Map
     * @param beginTime
     * @param endTime
     * @return
     */
    private Map<String, StandardStats> getStandardStatsMap(Date beginTime, Date endTime) {
        List<StandardStats> standardStatsList = instanceStatsDao.getStandardStatsByCreateTime(beginTime, endTime, ConstUtils.REDIS);
        // ??????host:port?????????
        Map<String, StandardStats> resultMap = new HashMap<String, StandardStats>();
        for (StandardStats standardStats : standardStatsList) {
            String hostPort = standardStats.getIp() + ":" + standardStats.getPort();
            resultMap.put(hostPort, standardStats);
        }
        return resultMap;
    }

    public void setInstanceAlertConfigDao(InstanceAlertConfigDao instanceAlertConfigDao) {
        this.instanceAlertConfigDao = instanceAlertConfigDao;
    }

    public void setInstanceStatsDao(InstanceStatsDao instanceStatsDao) {
        this.instanceStatsDao = instanceStatsDao;
    }

    public void setInstanceDao(InstanceDao instanceDao) {
        this.instanceDao = instanceDao;
    }

    public void setEmailComponent(EmailComponent emailComponent) {
        this.emailComponent = emailComponent;
    }

    public void setVelocityEngine(VelocityEngine velocityEngine) {
        this.velocityEngine = velocityEngine;
    }

    public void setAppDao(AppDao appDao) {
        this.appDao = appDao;
    }

}
