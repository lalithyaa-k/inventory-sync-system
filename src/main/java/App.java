import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Model Classes
class Product {
    private final String id;
    private final String name;
    private final String sku;
    
    public Product(String id, String name, String sku) {
        this.id = id;
        this.name = name;
        this.sku = sku;
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getSku() { return sku; }
}

class InventoryItem {
    private final String productId;
    private int quantity;
    private final String warehouseId;
    private LocalDateTime lastSyncTime;
    private int version;
    
    public InventoryItem(String productId, int quantity, String warehouseId) {
        this.productId = productId;
        this.quantity = quantity;
        this.warehouseId = warehouseId;
        this.lastSyncTime = LocalDateTime.now();
        this.version = 1;
    }
    
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getWarehouseId() { return warehouseId; }
    public LocalDateTime getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(LocalDateTime lastSyncTime) { this.lastSyncTime = lastSyncTime; }
    public int getVersion() { return version; }
    public void incrementVersion() { this.version++; }
}

// Repository
class InventoryRepository {
    private final ConcurrentHashMap<String, InventoryItem> inventoryStore = new ConcurrentHashMap<>();
    
    public void save(InventoryItem item) {
        inventoryStore.put(item.getProductId() + "_" + item.getWarehouseId(), item);
    }
    
    public InventoryItem findByProductAndWarehouse(String productId, String warehouseId) {
        return inventoryStore.get(productId + "_" + warehouseId);
    }
    
    public boolean updateStock(String productId, String warehouseId, int newQuantity, int expectedVersion) {
        String key = productId + "_" + warehouseId;
        InventoryItem current = inventoryStore.get(key);
        
        if (current != null && current.getVersion() == expectedVersion) {
            current.setQuantity(newQuantity);
            current.setLastSyncTime(LocalDateTime.now());
            current.incrementVersion();
            return true;
        }
        return false;
    }
}

// External Warehouse Service Simulator
class WarehouseService {
    private final Map<String, Integer> externalInventory = new HashMap<>();
    private final Random random = new Random();
    
    public WarehouseService() {
        externalInventory.put("PROD001_WARE001", 100);
        externalInventory.put("PROD002_WARE001", 50);
        externalInventory.put("PROD003_WARE002", 75);
    }
    
    public int getExternalStock(String productId, String warehouseId) {
        simulateNetworkDelay();
        String key = productId + "_" + warehouseId;
        return externalInventory.getOrDefault(key, 0);
    }
    
    public boolean updateExternalStock(String productId, String warehouseId, int quantity) {
        simulateNetworkDelay();
        String key = productId + "_" + warehouseId;
        
        if (random.nextInt(100) < 5) {
            throw new RuntimeException("External system temporarily unavailable");
        }
        
        externalInventory.put(key, quantity);
        return true;
    }
    
    private void simulateNetworkDelay() {
        try {
            Thread.sleep(random.nextInt(100) + 50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

// Inventory Service
class InventoryService {
    private final InventoryRepository repository;
    
    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }
    
    public void addOrUpdateInventory(InventoryItem item) {
        repository.save(item);
        System.out.println("[INFO] Inventory updated for product: " + item.getProductId() + 
                         ", warehouse: " + item.getWarehouseId() + 
                         ", quantity: " + item.getQuantity());
    }
    
    public InventoryItem getInventory(String productId, String warehouseId) {
        return repository.findByProductAndWarehouse(productId, warehouseId);
    }
    
    public boolean updateLocalStock(String productId, String warehouseId, int newQuantity, int version) {
        return repository.updateStock(productId, warehouseId, newQuantity, version);
    }
}

// Sync Result Class
class SyncResult {
    private final boolean success;
    private final String message;
    private final int previousLocalStock;
    private final int externalStock;
    
    public SyncResult(boolean success, String message, int previousLocalStock, int externalStock) {
        this.success = success;
        this.message = message;
        this.previousLocalStock = previousLocalStock;
        this.externalStock = externalStock;
    }
    
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public int getPreviousLocalStock() { return previousLocalStock; }
    public int getExternalStock() { return externalStock; }
}

// Main Sync Service
class SyncService {
    private final InventoryService inventoryService;
    private final WarehouseService warehouseService;
    private int syncCounter = 0;
    
    public SyncService(InventoryService inventoryService, WarehouseService warehouseService) {
        this.inventoryService = inventoryService;
        this.warehouseService = warehouseService;
    }
    
    public SyncResult syncFromWarehouse(String productId, String warehouseId) {
        System.out.println("[SYNC] Starting sync from warehouse for product: " + productId + 
                         ", warehouse: " + warehouseId);
        
        try {
            int externalStock = warehouseService.getExternalStock(productId, warehouseId);
            InventoryItem localItem = inventoryService.getInventory(productId, warehouseId);
            int localStock = (localItem != null) ? localItem.getQuantity() : 0;
            
            if (localStock == externalStock) {
                System.out.println("[SYNC] No sync needed - stocks already in sync");
                return new SyncResult(true, "Stocks already in sync", localStock, externalStock);
            }
            
            if (localItem != null) {
                inventoryService.updateLocalStock(productId, warehouseId, externalStock, localItem.getVersion());
            } else {
                InventoryItem newItem = new InventoryItem(productId, externalStock, warehouseId);
                inventoryService.addOrUpdateInventory(newItem);
            }
            
            syncCounter++;
            System.out.println("[SYNC] Sync completed successfully");
            return new SyncResult(true, "Sync completed successfully", localStock, externalStock);
            
        } catch (Exception e) {
            System.err.println("[ERROR] Sync failed: " + e.getMessage());
            throw new RuntimeException("Failed to sync from warehouse: " + e.getMessage(), e);
        }
    }
    
    public SyncResult syncBidirectional(String productId, String warehouseId, int maxRetries) {
        SyncResult result = null;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            attempt++;
            System.out.println("[SYNC] Attempt " + attempt + "/" + maxRetries + 
                             " for product: " + productId + ", warehouse: " + warehouseId);
            
            try {
                int externalStock = warehouseService.getExternalStock(productId, warehouseId);
                InventoryItem localItem = inventoryService.getInventory(productId, warehouseId);
                int localStock = (localItem != null) ? localItem.getQuantity() : 0;
                
                if (Math.abs(localStock - externalStock) > 10) {
                    result = syncFromWarehouse(productId, warehouseId);
                } else {
                    int reconciledStock = (localStock + externalStock) / 2;
                    
                    if (localItem != null) {
                        inventoryService.updateLocalStock(productId, warehouseId, reconciledStock, localItem.getVersion());
                    } else {
                        InventoryItem newItem = new InventoryItem(productId, reconciledStock, warehouseId);
                        inventoryService.addOrUpdateInventory(newItem);
                    }
                    
                    warehouseService.updateExternalStock(productId, warehouseId, reconciledStock);
                    result = new SyncResult(true, "Bidirectional sync completed", localStock, externalStock);
                    syncCounter++;
                }
                
                return result;
                
            } catch (Exception e) {
                System.err.println("[WARN] Attempt " + attempt + " failed: " + e.getMessage());
                if (attempt == maxRetries) {
                    throw new RuntimeException("Sync failed after " + maxRetries + " attempts: " + e.getMessage(), e);
                }
                
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Sync interrupted", ie);
                }
            }
        }
        
        return result;
    }
    
    public int getTotalSyncCount() {
        return syncCounter;
    }
}

// Main Application Class
public class App {
    public static void main(String[] args) {
        System.out.println("=== Inventory Sync System Started ===");
        
        // Initialize services
        InventoryRepository repository = new InventoryRepository();
        InventoryService inventoryService = new InventoryService(repository);
        WarehouseService warehouseService = new WarehouseService();
        SyncService syncService = new SyncService(inventoryService, warehouseService);
        
        // Test synchronization tasks
        List<String[]> syncTasks = Arrays.asList(
            new String[]{"PROD001", "WARE001"},
            new String[]{"PROD002", "WARE001"},
            new String[]{"PROD003", "WARE002"},
            new String[]{"PROD004", "WARE001"}
        );
        
        // Run sync tasks
        for (String[] task : syncTasks) {
            String productId = task[0];
            String warehouseId = task[1];
            
            System.out.println("\n--- Processing sync for " + productId + ":" + warehouseId + " ---");
            
            try {
                SyncResult result = syncService.syncBidirectional(productId, warehouseId, 3);
                
                if (result.isSuccess()) {
                    System.out.println("SUCCESS: " + result.getMessage());
                    System.out.println("  Previous local stock: " + result.getPreviousLocalStock());
                    System.out.println("  External stock: " + result.getExternalStock());
                }
                
                InventoryItem finalItem = inventoryService.getInventory(productId, warehouseId);
                if (finalItem != null) {
                    System.out.println("  Final stock level: " + finalItem.getQuantity());
                    System.out.println("  Version: " + finalItem.getVersion());
                }
                
            } catch (Exception e) {
                System.err.println("FAILED: " + e.getMessage());
            }
        }
        
        System.out.println("\n=== Sync Summary ===");
        System.out.println("Total successful sync operations: " + syncService.getTotalSyncCount());
        System.out.println("=== Inventory Sync System Shutdown ===");
    }
}