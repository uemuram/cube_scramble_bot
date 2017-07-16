package jp.gr.java_conf.mu.csb.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class DynamoDBUtil {

	private LambdaLogger logger;
	private AmazonDynamoDB dynamoDBClient;
	private final static int RETRYCOUNT_PUTITEM = 3;
	private final static int RETRYINTERVAL_PUTITEM = 30000;

	// �R���X�g���N�^
	public DynamoDBUtil(LambdaLogger logger) {
		this.logger = logger;
		this.dynamoDBClient = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.AP_NORTHEAST_1).build();
	}

	// �A�C�e����1���擾(hashName = hashValue�̏����Ō���)
	public Map<String, AttributeValue> getItem(String tableName, String hashName, String hashValue) {
		logger.log("--------------getItem start--------------");
		logger.log("�e�[�u����: " + tableName);
		logger.log("����: " + hashName + "=" + hashValue);

		// ��������
		Map<String, AttributeValue> key = new HashMap<String, AttributeValue>();
		key.put(hashName, new AttributeValue().withS(hashValue));
		GetItemRequest getItemRequest = new GetItemRequest().withTableName(tableName).withKey(key);

		// �������{
		GetItemResult result = dynamoDBClient.getItem(getItemRequest);
		printItem(result.getItem(), "get");
		logger.log("--------------getItem end----------------");

		return result.getItem();
	}

	// �A�C�e����1���擾(hashName = hashValue�ArangeName = rangeValue�̏����Ō���)
	public Map<String, AttributeValue> getItem(String tableName, String hashName, String hashValue, String rangeName,
			String rangeValue) {
		logger.log("--------------getItem start--------------");
		logger.log("�e�[�u����: " + tableName);
		logger.log("����: " + hashName + "=" + hashValue + "," + rangeName + "=" + rangeValue);

		// ��������
		Map<String, AttributeValue> key = new HashMap<String, AttributeValue>();
		key.put(hashName, new AttributeValue().withS(hashValue));
		key.put(rangeName, new AttributeValue().withS(rangeValue));

		// �������{
		GetItemRequest getItemRequest = new GetItemRequest().withTableName(tableName).withKey(key);
		GetItemResult result = dynamoDBClient.getItem(getItemRequest);
		printItem(result.getItem(), "get");
		logger.log("--------------getItem end----------------");

		return result.getItem();
	}

	// �A�C�e��1����o�^
	public void putItem(String tableName, Map<String, AttributeValue> item) {
		// �o�^�����ǉ�
		String updateDate = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(LocalDateTime.now());
		item.put("update_date", new AttributeValue().withS(updateDate));
		// ����
		PutItemRequest putItemRequest = new PutItemRequest().withTableName(tableName).withItem(item);
		int count = 0;
		boolean success = false;
		// �o�^���{
		do {
			try {
				dynamoDBClient.putItem(putItemRequest);
				success = true;
				printItem(item, "put");
			} catch (Exception e1) {
				logger.log("putItem���s : " + e1.getMessage());
				// ���s�����ꍇ�͑ҋ@��ɍĎ��s
				if (count < RETRYCOUNT_PUTITEM) {
					try {
						Thread.sleep(RETRYINTERVAL_PUTITEM);
					} catch (InterruptedException e2) {
					}
				}
			}
		} while (!success && count < RETRYCOUNT_PUTITEM);
	}

	// �A�C�e��1�����X�V
	public void updateItem(String tableName, Map<String, AttributeValue> key, Map<String, AttributeValueUpdate> item) {
		// �o�^�����ǉ�
		String updateDate = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(LocalDateTime.now());
		item.put("update_date", new AttributeValueUpdate().withAction(AttributeAction.PUT)
				.withValue(new AttributeValue().withS(updateDate)));
		// ����
		UpdateItemRequest updateItemRequest = new UpdateItemRequest().withTableName(tableName).withKey(key)
				.withAttributeUpdates(item);

		int count = 0;
		boolean success = false;
		// �o�^���{
		do {
			try {
				dynamoDBClient.updateItem(updateItemRequest);
				success = true;
				printItem(key, item, "update");
			} catch (Exception e1) {
				logger.log("putItem���s : " + e1.getMessage());
				// ���s�����ꍇ�͑ҋ@��ɍĎ��s
				if (count < RETRYCOUNT_PUTITEM) {
					try {
						Thread.sleep(RETRYINTERVAL_PUTITEM);
					} catch (InterruptedException e2) {
					}
				}
			}
		} while (!success && count < RETRYCOUNT_PUTITEM);
	}

	// �A�C�e���̏���\��
	public void printItem(Map<String, AttributeValue> updateKey, Map<String, AttributeValueUpdate> updateItem,
			String message) {
		logger.log("-------DynamoDBItem(" + message + ")-------");
		logger.log("--updateKey--");

		if (updateKey == null) {
			logger.log("��̃L�[");
		} else {
			for (Map.Entry<String, AttributeValue> item : updateKey.entrySet()) {
				String attributeName = item.getKey();
				AttributeValue value = item.getValue();
				logger.log(attributeName + " " + (value.getS() == null ? "" : "S=[" + value.getS() + "]")
						+ (value.getN() == null ? "" : "N=[" + value.getN() + "]")
						+ (value.getB() == null ? "" : "B=[" + value.getB() + "]")
						+ (value.getSS() == null ? "" : "SS=[" + value.getSS() + "]")
						+ (value.getNS() == null ? "" : "NS=[" + value.getNS() + "]")
						+ (value.getBS() == null ? "" : "BS=[" + value.getBS() + "] n"));
			}
		}

		logger.log("--updateValue--");
		if (updateItem == null) {
			logger.log("��̃A�C�e��");
		} else {
			for (Entry<String, AttributeValueUpdate> item : updateItem.entrySet()) {
				String attributeName = item.getKey();
				AttributeValue value = item.getValue().getValue();
				logger.log(attributeName + " " + (value.getS() == null ? "" : "S=[" + value.getS() + "]")
						+ (value.getN() == null ? "" : "N=[" + value.getN() + "]")
						+ (value.getB() == null ? "" : "B=[" + value.getB() + "]")
						+ (value.getSS() == null ? "" : "SS=[" + value.getSS() + "]")
						+ (value.getNS() == null ? "" : "NS=[" + value.getNS() + "]")
						+ (value.getBS() == null ? "" : "BS=[" + value.getBS() + "] n"));
			}
		}
		logger.log("--------------------------");
	}

	// �A�C�e���̏���\��
	public void printItem(Map<String, AttributeValue> attributeList, String message) {
		logger.log("-------DynamoDBItem(" + message + ")-------");

		if (attributeList == null) {
			logger.log("��A�C�e��");
		} else {
			for (Map.Entry<String, AttributeValue> item : attributeList.entrySet()) {
				String attributeName = item.getKey();
				AttributeValue value = item.getValue();
				logger.log(attributeName + " " + (value.getS() == null ? "" : "S=[" + value.getS() + "]")
						+ (value.getN() == null ? "" : "N=[" + value.getN() + "]")
						+ (value.getB() == null ? "" : "B=[" + value.getB() + "]")
						+ (value.getSS() == null ? "" : "SS=[" + value.getSS() + "]")
						+ (value.getNS() == null ? "" : "NS=[" + value.getNS() + "]")
						+ (value.getBS() == null ? "" : "BS=[" + value.getBS() + "] n"));
			}
		}
		logger.log("--------------------------");
	}

	// �A�C�e���̏���\��
	public void printItem(Map<String, AttributeValue> attributeList) {
		printItem(attributeList, "");
	}
}
