package jp.gr.java_conf.mu.csb.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
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

	// public void getItem(String tableName, String hashName, int id) {
	// System.out.println("--- "111 + tableName + " ---");
	// Map<String, AttributeValue> key = new HashMap<String, AttributeValue>();
	// key.put(hashName, new AttributeValue().withN(Integer.toString(id)));
	// GetItemRequest getItemRequest = new
	// GetItemRequest().withTableName(tableName).withKey(key);
	// GetItemResult result = dynamoDBClient.getItem(getItemRequest);
	// printItem(result.getItem());
	// }

	// �A�C�e����1���擾(hashName = hashValue�̏����Ō���)
	public GetItemResult getItem(String tableName, String hashName, String hashValue) {
		logger.log("-------getItem(" + tableName + ", " + hashName + "=" + hashValue + ")-------");

		// ��������
		Map<String, AttributeValue> key = new HashMap<String, AttributeValue>();
		key.put(hashName, new AttributeValue().withS(hashValue));
		GetItemRequest getItemRequest = new GetItemRequest().withTableName(tableName).withKey(key);

		// �������{
		GetItemResult result = dynamoDBClient.getItem(getItemRequest);
		printItem(result.getItem(), "get");

		return result;
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

	// �A�C�e���̏���\��
	public void printItem(Map<String, AttributeValue> attributeList, String message) {
		logger.log("-------DynamoDBItem(" + message + ")-------");
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
		logger.log("--------------------------");
	}

	// �A�C�e���̏���\��
	public void printItem(Map<String, AttributeValue> attributeList) {
		printItem(attributeList, "");
	}
}
