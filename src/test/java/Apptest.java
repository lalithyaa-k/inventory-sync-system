import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    
    private InventoryRepository repository;
    private InventoryService inventoryService;
    private WarehouseService warehouseService;
    private SyncService syncService;
    
    @BeforeEach
    void setUp() {
        repository = new InventoryRepository();
        inventoryService = new InventoryService(repository);
        warehouseService = new WarehouseService();
        syncService = new SyncService(inventoryService, warehouseService);
    }
    
    @Test
    void testInventoryItemCreation() {
        InventoryItem item = new InventoryItem("TEST001", 100, "WARE001");
        inventoryService.addOrUpdateInventory(item);
        
        InventoryItem retrieved = inventoryService.getInventory("TEST001", "WARE001");
        assertNotNull(retrieved);
        assertEquals(100, retrieved.getQuantity());
    }
    
    @Test
    void testUpdateLocalStock() {
        InventoryItem item = new InventoryItem("TEST002", 50, "WARE001");
        inventoryService.addOrUpdateInventory(item);
        
        boolean updated = inventoryService.updateLocalStock("TEST002", "WARE001", 75, 1);
        assertTrue(updated);
        
        InventoryItem retrieved = inventoryService.getInventory("TEST002", "WARE001");
        assertEquals(75, retrieved.getQuantity());
    }
    
    @Test
    void testOptimisticLocking() {
        InventoryItem item = new InventoryItem("TEST003", 50, "WARE001");
        inventoryService.addOrUpdateInventory(item);
        
        boolean updated = inventoryService.updateLocalStock("TEST003", "WARE001", 75, 999);
        assertFalse(updated);
        
        updated = inventoryService.updateLocalStock("TEST003", "WARE001", 75, 1);
        assertTrue(updated);
        
        InventoryItem retrieved = inventoryService.getInventory("TEST003", "WARE001");
        assertEquals(75, retrieved.getQuantity());
        assertEquals(2, retrieved.getVersion());
    }
    
    @Test
    void testSyncFromWarehouse() {
        SyncResult result = syncService.syncFromWarehouse("PROD001", "WARE001");
        assertTrue(result.isSuccess());
        
        InventoryItem retrieved = inventoryService.getInventory("PROD001", "WARE001");
        assertNotNull(retrieved);
        assertEquals(100, retrieved.getQuantity());
    }
}