package org.foreverempty.common.handler;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry.*;
import com.alibaba.otter.canal.protocol.Message;
import lombok.extern.slf4j.Slf4j;
import java.net.InetSocketAddress;

import java.util.List;

@Slf4j
public abstract class AbstractCanalHandler {

    protected abstract String getDestination();

    protected abstract void processRowData(EventType eventType, List<Column> beforeColumns, List<Column> afterColumns);

    public void start() {
        new Thread(() -> {
            CanalConnector connector = CanalConnectors.newSingleConnector(
                    new InetSocketAddress("127.0.0.1", 11111),
                    "example",
                    "",
                    ""
            );

            try {
                connector.connect();
                connector.subscribe(getDestination());
                connector.rollback();
                log.info("Start to listen, target: {}", getDestination());

                while (true) {
                    Message message = connector.getWithoutAck(100);
                    long batchId = message.getId();
                    if (batchId != -1 && !message.getEntries().isEmpty()) {
                        handleEntries(message.getEntries());
                    }
                }
            } catch (Exception e) {
                log.error("Error while handling entries: {}", e.getMessage(), e);
            } finally {
                connector.disconnect();
            }
        }).start();
    }

    private void handleEntries(List<Entry> entries) throws Exception {
        for (Entry entry : entries) {
            if (entry.getEntryType() == EntryType.ROWDATA) {
                RowChange rowChange = RowChange.parseFrom(entry.getStoreValue());
                EventType eventType = rowChange.getEventType();
                for (RowData rowData : rowChange.getRowDatasList()) {
                    processRowData(eventType, rowData.getBeforeColumnsList(), rowData.getAfterColumnsList());
                }
            }
        }
    }

    protected String getVal(List<Column> columns, String name) {
        return columns.stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .map(Column::getValue)
                .findFirst().orElse(null);
    }
}
