-- MySQL dump 10.13  Distrib 5.7.29, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: group_buy_market
-- ------------------------------------------------------
-- Server version	8.0.32

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `group_buy_order`
--

DROP TABLE IF EXISTS `group_buy_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `group_buy_order` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `team_id` varchar(8) NOT NULL COMMENT '拼单组队ID',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `source` varchar(8) NOT NULL COMMENT '渠道',
  `channel` varchar(8) NOT NULL COMMENT '来源',
  `original_price` decimal(8,2) NOT NULL COMMENT '原始价格',
  `deduction_price` decimal(8,2) NOT NULL COMMENT '折扣金额',
  `pay_price` decimal(8,2) NOT NULL COMMENT '支付价格',
  `target_count` int NOT NULL COMMENT '目标数量',
  `complete_count` int NOT NULL COMMENT '完成数量',
  `lock_count` int NOT NULL COMMENT '锁单数量',
  `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '状态（0-拼单中、1-完成、2-失败）',
  `valid_start_time` datetime NOT NULL COMMENT '拼团开始时间',
  `valid_end_time` datetime NOT NULL COMMENT '拼团结束时间',
  `notify_url` varchar(512) NOT NULL COMMENT '回调地址',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_team_id` (`team_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `group_buy_order`
--

LOCK TABLES `group_buy_order` WRITE;
/*!40000 ALTER TABLE `group_buy_order` DISABLE KEYS */;
INSERT INTO `group_buy_order` VALUES (3,'80759049',100123,'s01','c01',100.00,10.00,90.00,3,3,3,1,'2025-01-31 17:28:19','2025-01-31 18:28:19','http://127.0.0.1:8091/api/v1/test/group_buy_notify','2025-01-31 17:28:19','2025-01-31 17:51:38'),(4,'82218038',100123,'s01','c01',100.00,10.00,90.00,3,3,3,1,'2026-09-03 09:54:55','2026-09-03 10:54:55','http://127.0.0.1:8091/api/v1/test/group_buy_notify','2026-09-03 09:54:55','2026-09-03 10:02:06'),(5,'31875567',100123,'s01','c01',100.00,10.00,90.00,3,0,1,0,'2026-09-03 17:08:24','2026-09-03 18:08:24','http://localhost:8091/api/v1/test/group_buy_notify','2026-09-03 17:08:23','2026-09-03 17:08:23'),(14,'43673392',100123,'s01','c01',100.00,10.00,90.00,3,0,1,0,'2026-09-06 11:05:28','2026-09-06 12:05:28','http://127.0.0.1:8091/api/v1/test/group_buy_notify','2026-09-06 11:05:28','2026-09-06 11:05:28'),(15,'48034975',100123,'s01','c01',100.00,10.00,90.00,3,0,1,0,'2026-09-06 11:10:35','2026-09-06 12:10:35','http://127.0.0.1:8091/api/v1/test/group_buy_notify','2026-09-06 11:10:35','2026-09-06 11:10:35');
/*!40000 ALTER TABLE `group_buy_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `group_buy_order_list`
--

DROP TABLE IF EXISTS `group_buy_order_list`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `group_buy_order_list` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `user_id` varchar(64) NOT NULL COMMENT '用户ID',
  `team_id` varchar(8) NOT NULL COMMENT '拼单组队ID',
  `order_id` varchar(12) NOT NULL COMMENT '订单ID',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `start_time` datetime NOT NULL COMMENT '活动开始时间',
  `end_time` datetime NOT NULL COMMENT '活动结束时间',
  `goods_id` varchar(16) NOT NULL COMMENT '商品ID',
  `source` varchar(8) NOT NULL COMMENT '渠道',
  `channel` varchar(8) NOT NULL COMMENT '来源',
  `original_price` decimal(8,2) NOT NULL COMMENT '原始价格',
  `deduction_price` decimal(8,2) NOT NULL COMMENT '折扣金额',
  `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '状态；0初始锁定、1消费完成、2用户退单',
  `out_trade_no` varchar(12) NOT NULL COMMENT '外部交易单号-确保外部调用唯一幂等',
  `out_trade_time` datetime DEFAULT NULL COMMENT '外部交易时间',
  `biz_id` varchar(64) NOT NULL COMMENT '业务唯一ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_order_id` (`order_id`),
  KEY `idx_user_id_activity_id` (`user_id`,`activity_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `group_buy_order_list`
--

LOCK TABLES `group_buy_order_list` WRITE;
/*!40000 ALTER TABLE `group_buy_order_list` DISABLE KEYS */;
INSERT INTO `group_buy_order_list` VALUES (1,'xfg01','80759049','768908837527',100123,'2024-12-07 10:19:40','2026-12-07 10:19:40','9890001','s01','c01',100.00,10.00,1,'555024425070','2025-01-31 17:31:49','100123_xfg01_1','2025-01-31 17:28:19','2025-01-31 17:31:48'),(2,'xfg02','80759049','873986192460',100123,'2024-12-07 10:19:40','2026-12-07 10:19:40','9890001','s01','c01',100.00,10.00,1,'812787347025','2025-01-31 17:32:09','100123_xfg02_1','2025-01-31 17:30:29','2025-01-31 17:32:09'),(3,'xfg03','80759049','490257193870',100123,'2024-12-07 10:19:40','2026-12-07 10:19:40','9890001','s01','c01',100.00,10.00,1,'536311764349','2025-01-31 17:51:38','100123_xfg03_1','2025-01-31 17:31:22','2025-01-31 17:51:38'),(4,'zj01','82218038','791983518044',100123,'2024-12-07 10:19:40','2026-12-07 10:19:40','9890001','s01','c01',10.00,10.00,1,'190059889788','2026-09-03 10:01:03','100123_zj01_1','2026-09-03 09:54:55','2026-09-03 10:01:03'),(5,'zj02','82218038','627342783625',100123,'2024-12-07 10:19:40','2026-12-07 10:19:40','9890001','s01','c01',10.00,10.00,1,'551056502038','2026-09-03 10:01:36','100123_zj02_1','2026-09-03 09:57:08','2026-09-03 10:01:36'),(6,'zj03','82218038','096626335828',100123,'2024-12-07 10:19:40','2026-12-07 10:19:40','9890001','s01','c01',10.00,10.00,1,'404928039080','2026-09-03 10:02:07','100123_zj03_1','2026-09-03 09:57:31','2026-09-03 10:02:06'),(7,'testuser','31875567','165371129075',100123,'2024-12-07 10:19:40','2026-12-07 10:19:40','9890001','s01','c01',10.00,10.00,0,'OT100','2026-09-03 17:08:24','100123_testuser_1','2026-09-03 17:08:23','2026-09-03 17:08:23'),(8,'xiaofuge','43673392','226665625459',100123,'2024-12-07 10:19:40','2026-12-07 10:19:40','9890001','s01','c01',10.00,10.00,0,'fmtp8bjsfoh0','2026-09-06 11:05:28','100123_xiaofuge_1','2026-09-06 11:05:28','2026-09-06 11:05:28'),(9,'liergou','48034975','336671808347',100123,'2024-12-07 10:19:40','2026-12-07 10:19:40','9890001','s01','c01',10.00,10.00,0,'fmtp8i4nzeiu','2026-09-06 11:10:35','100123_liergou_1','2026-09-06 11:10:35','2026-09-06 11:10:35');
/*!40000 ALTER TABLE `group_buy_order_list` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `group_buy_activity`
--

DROP TABLE IF EXISTS `group_buy_activity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `group_buy_activity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `activity_name` varchar(128) NOT NULL COMMENT '活动名称',
  `discount_id` varchar(8) NOT NULL COMMENT '折扣ID',
  `group_type` tinyint(1) NOT NULL DEFAULT '0' COMMENT '拼团方式（0自动成团、1达成目标拼团）',
  `take_limit_count` int NOT NULL DEFAULT '1' COMMENT '拼团次数限制',
  `target` int NOT NULL DEFAULT '1' COMMENT '拼团目标',
  `valid_time` int NOT NULL DEFAULT '15' COMMENT '拼团时长（分钟）',
  `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '活动状态（0创建、1生效、2过期、3废弃）',
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '活动开始时间',
  `end_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '活动结束时间',
  `tag_id` varchar(32) DEFAULT NULL COMMENT '人群标签规则标识',
  `tag_scope` varchar(4) DEFAULT NULL COMMENT '人群标签规则范围（多选；1可见限制、2参与限制）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_activity_id` (`activity_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团活动';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `group_buy_activity`
--

LOCK TABLES `group_buy_activity` WRITE;
/*!40000 ALTER TABLE `group_buy_activity` DISABLE KEYS */;
INSERT INTO `group_buy_activity` VALUES (1,100123,'测试活动','25120208',0,1,3,60,1,'2024-12-07 10:19:40','2026-12-07 10:19:40','RQ_KJHKL98UU78H66554GFDV','1,2','2024-12-07 10:19:40','2025-01-30 15:01:53');
/*!40000 ALTER TABLE `group_buy_activity` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notify_task`
--

DROP TABLE IF EXISTS `notify_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `notify_task` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `team_id` varchar(8) NOT NULL COMMENT '拼单组队ID',
  `notify_url` varchar(128) NOT NULL COMMENT '回调接口',
  `notify_count` int NOT NULL COMMENT '回调次数',
  `notify_status` tinyint(1) NOT NULL COMMENT '回调状态【0初始、1完成、2重试、3失败】',
  `parameter_json` varchar(256) NOT NULL COMMENT '参数对象',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_team_id` (`team_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notify_task`
--

LOCK TABLES `notify_task` WRITE;
/*!40000 ALTER TABLE `notify_task` DISABLE KEYS */;
INSERT INTO `notify_task` VALUES (1,100123,'46832479','暂无',1,1,'{\"teamId\":\"46832479\",\"outTradeNoList\":[\"581909866926\",\"155123092895\",\"451517755304\"]}','2025-01-26 19:11:46','2025-01-31 17:21:30'),(2,100123,'38795123','暂无',1,1,'{\"teamId\":\"38795123\",\"outTradeNoList\":[\"134597814295\",\"154310924273\",\"228984300880\"]}','2025-01-28 08:27:26','2025-01-31 17:21:30'),(3,100123,'57199993','暂无',1,1,'{\"teamId\":\"57199993\",\"outTradeNoList\":[\"038426231487\",\"652896391719\",\"619401409195\"]}','2025-01-28 09:13:00','2025-01-31 17:21:30'),(9,100123,'80759049','http://127.0.0.1:8091/api/v1/test/group_buy_notify',1,1,'{\"teamId\":\"80759049\",\"outTradeNoList\":[\"555024425070\",\"812787347025\",\"536311764349\"]}','2025-01-31 17:51:39','2025-01-31 17:52:10'),(10,100123,'82218038','http://127.0.0.1:8091/api/v1/test/group_buy_notify',1,1,'{\"teamId\":\"82218038\",\"outTradeNoList\":[\"190059889788\",\"551056502038\",\"404928039080\"]}','2026-09-03 10:02:06','2026-09-03 10:41:45');
/*!40000 ALTER TABLE `notify_task` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-06 15:48:41
