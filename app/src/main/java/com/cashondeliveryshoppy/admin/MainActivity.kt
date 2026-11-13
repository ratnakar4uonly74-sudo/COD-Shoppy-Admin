package com.cashondeliveryshoppy.admin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ADMIN_EMAIL = "cashondeliveryshoppy@gmail.com"

class MainActivity : ComponentActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CODAdminApp(auth, db) }
    }
}

data class Product(
    val id: String, val name: String, val price: Double,
    val description: String, val image: String, val active: Boolean
)
data class Order(
    val id: String, val productName: String, val qty: Long, val total: Double,
    val name: String, val phone: String, val address: String, val city: String,
    val state: String, val pincode: String, val email: String, val status: String,
    val shippingCompany: String, val shippingId: String, val instructions: String,
    val createdAt: Date?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CODAdminApp(auth: FirebaseAuth, db: FirebaseFirestore) {
    val context = LocalContext.current
    var signedIn by remember { mutableStateOf(auth.currentUser != null) }
    var currentEmail by remember { mutableStateOf(auth.currentUser?.email.orEmpty()) }
    var tab by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }

    fun loadProducts() {
        db.collection("products").orderBy("createdAt", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { snap ->
                products = snap.documents.map { d ->
                    Product(d.id, d.getString("name") ?: "", d.getDouble("price") ?: 0.0,
                        d.getString("description") ?: "", d.getString("image") ?: "",
                        d.getBoolean("active") ?: true)
                }
            }.addOnFailureListener { message = it.localizedMessage ?: "Products load failed" }
    }
    fun loadOrders() {
        db.collection("orders").orderBy("createdAt", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { snap ->
                orders = snap.documents.map { d ->
                    val ts = d.getTimestamp("createdAt")
                    Order(d.id, d.getString("productName") ?: "", d.getLong("qty") ?: 1,
                        d.getDouble("total") ?: 0.0, d.getString("name") ?: "",
                        d.getString("phone") ?: "", d.getString("address") ?: "",
                        d.getString("city") ?: "", d.getString("state") ?: "",
                        d.getString("pincode") ?: "", d.getString("email") ?: "",
                        d.getString("status") ?: "new", d.getString("shippingCompany") ?: "",
                        d.getString("shippingId") ?: "", d.getString("instructions") ?: "",
                        ts?.toDate())
                }
            }.addOnFailureListener { message = it.localizedMessage ?: "Orders load failed" }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(com.google.android.gms.common.api.ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnFailureListener { message = it.localizedMessage ?: "Firebase login failed" }
            } catch (e: Exception) {
                message = e.localizedMessage ?: "Google sign-in failed"
            }
        } else {
            message = "Google sign-in was cancelled. Please try again."
        }
    }

    LaunchedEffect(signedIn) {
        if (signedIn && currentEmail.equals(ADMIN_EMAIL, true)) {
            loadProducts()
            loadOrders()
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("COD Shoppy Admin", fontWeight = FontWeight.Bold) },
                    actions = {
                        if (signedIn) IconButton(onClick = {
                            if (tab == 0) loadProducts() else loadOrders()
                        }) { Icon(Icons.Default.Refresh, "Refresh") }
                        if (signedIn) IconButton(onClick = {
                            auth.signOut()
                            signedIn = false
                            currentEmail = ""
                        }) { Icon(Icons.Default.Logout, "Logout") }
                    }
                )
            },
            bottomBar = {
                if (signedIn && currentEmail.equals(ADMIN_EMAIL, true)) {
                    NavigationBar {
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.Inventory2, null) }, label = { Text("Products") })
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.LocalShipping, null) }, label = { Text("Orders") })
                    }
                }
            }
        ) { padding ->
            if (!signedIn) {
                LoginScreen(
                    modifier = Modifier.padding(padding),
                    onLogin = {
                        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                        if (id == 0) {
                            message = "Firebase setup incomplete: add google-services.json to the app folder."
                        } else {
                            val clientId = context.getString(id)
                            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(clientId).requestEmail().build()
                            val client = GoogleSignIn.getClient(context, options)
                            signInLauncher.launch(client.signInIntent)
                        }
                    },
                    message = message
                )
            } else if (!currentEmail.equals(ADMIN_EMAIL, true)) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("This Google account is not the store admin.", color = MaterialTheme.colorScheme.error)
                    Text(currentEmail)
                    Button(onClick = { auth.signOut(); signedIn = false }) { Text("Sign out") }
                }
            } else if (tab == 0) {
                ProductsScreen(
                    modifier = Modifier.padding(padding), products = products, message = message,
                    onAdd = { name, price, desc, image ->
                        busy = true
                        db.collection("products").add(hashMapOf(
                            "name" to name, "price" to price, "description" to desc,
                            "image" to image, "active" to true, "createdAt" to FieldValue.serverTimestamp()
                        )).addOnSuccessListener {
                            message = "Product uploaded! It will appear on the customer website."
                            loadProducts(); busy = false
                        }.addOnFailureListener {
                            message = it.localizedMessage ?: "Upload failed"; busy = false
                        }
                    },
                    onToggle = { p ->
                        db.collection("products").document(p.id).update("active", !p.active)
                            .addOnSuccessListener { loadProducts() }
                            .addOnFailureListener { message = it.localizedMessage ?: "Update failed" }
                    },
                    onDelete = { p ->
                        db.collection("products").document(p.id).delete()
                            .addOnSuccessListener { loadProducts() }
                            .addOnFailureListener { message = it.localizedMessage ?: "Delete failed" }
                    },
                    busy = busy
                )
            } else {
                OrdersScreen(
                    modifier = Modifier.padding(padding), orders = orders, message = message,
                    onStatus = { order, status ->
                        db.collection("orders").document(order.id).update("status", status)
                            .addOnSuccessListener { message = "Order status updated."; loadOrders() }
                            .addOnFailureListener { message = it.localizedMessage ?: "Update failed" }
                    },
                    onShip = { order, company, tracking ->
                        if (company.isBlank() || tracking.isBlank()) {
                            message = "Enter both courier company and tracking ID."
                        } else {
                            db.collection("orders").document(order.id).update(
                                mapOf("shippingCompany" to company, "shippingId" to tracking,
                                    "status" to "shipped", "shippedAt" to FieldValue.serverTimestamp())
                            ).addOnSuccessListener { message = "Order marked as shipped."; loadOrders() }
                                .addOnFailureListener { message = it.localizedMessage ?: "Shipping update failed" }
                        }
                    }
                )
            }
        }
    }

    // Observe Firebase sign-in changes including the Google account result.
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebase ->
            signedIn = firebase.currentUser != null
            currentEmail = firebase.currentUser?.email.orEmpty()
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
}

@Composable
fun LoginScreen(modifier: Modifier = Modifier, onLogin: () -> Unit, message: String) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🛍️", style = MaterialTheme.typography.displayLarge)
        Text("COD Shoppy Admin", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Sign in with your store Google account to manage products and orders.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    modifier: Modifier, products: List<Product>, message: String,
    onAdd: (String, Double, String, String) -> Unit,
    onToggle: (Product) -> Unit, onDelete: (Product) -> Unit, busy: Boolean
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageData by remember { mutableStateOf("") }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = decodeAndResize(context, uri)
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 72, output)
                imageData = "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                imageBitmap = bitmap
            } catch (e: Exception) { imageData = ""; imageBitmap = null }
        }
    }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Add new product", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, { name = it }, label = { Text("Product name *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(price, { price = it }, label = { Text("Price (₹) *") },
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(description, { description = it }, label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp))
                        Text(if (imageData.isBlank()) "Choose photo from Gallery" else "Change photo")
                    }
                    imageBitmap?.let {
                        Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 190.dp))
                    }
                    Button(
                        enabled = !busy && name.isNotBlank() && (price.toDoubleOrNull() ?: 0.0) > 0 && imageData.isNotBlank(),
                        onClick = {
                            onAdd(name.trim(), price.toDouble(), description.trim(), imageData)
                            name = ""; price = ""; description = ""; imageData = ""; imageBitmap = null
                        }, modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Uploading…" else "UPLOAD PRODUCT") }
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { Text("Your products", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (products.isEmpty()) item { Text("No products found yet.") }
        items(products, key = { it.id }) { p ->
            ElevatedCard {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("₹${"%.2f".format(p.price)}")
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (p.active) "Visible on website" else "Hidden")
                    Row {
                        TextButton(onClick = { onToggle(p) }) { Text(if (p.active) "Hide" else "Show") }
                        TextButton(onClick = { onDelete(p) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
fun OrdersScreen(
    modifier: Modifier, orders: List<Order>, message: String,
    onStatus: (Order, String) -> Unit, onShip: (Order, String, String) -> Unit
) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (message.isNotBlank()) item { Text(message, color = MaterialTheme.colorScheme.primary) }
        if (orders.isEmpty()) item { Text("No orders yet.") }
        items(orders, key = { it.id }) { o ->
            var company by remember(o.id) { mutableStateOf(o.shippingCompany) }
            var tracking by remember(o.id) { mutableStateOf(o.shippingId) }
            var status by remember(o.id) { mutableStateOf(o.status) }
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(o.productName + " × " + o.qty, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("COD Total: ₹${o.total}")
                    Text("${o.name}  •  ${o.phone}")
                    Text("${o.address}, ${o.city}, ${o.state} - ${o.pincode}")
                    if (o.email.isNotBlank()) Text(o.email, style = MaterialTheme.typography.bodySmall)
                    if (o.instructions.isNotBlank()) Text("Instructions: ${o.instructions}")
                    o.createdAt?.let { Text(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it)) }
                    Text("Order ID: ${o.id}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(company, { company = it }, label = { Text("Courier company") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tracking, { tracking = it }, label = { Text("Tracking ID") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onShip(o, company, tracking) }, modifier = Modifier.fillMaxWidth()) {
                        Text("SAVE & MARK SHIPPED")
                    }
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(status.replace("_", " "), {}, readOnly = true,
                            label = { Text("Order status") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("new", "confirmed", "out_for_delivery", "delivered", "cancelled").forEach { s ->
                                DropdownMenuItem(text = { Text(s.replace("_", " ")) }, onClick = {
                                    status = s; expanded = false; onStatus(o, s)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeAndResize(context: Context, uri: Uri): Bitmap {
    val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open selected image")
    val bytes = input.use { it.readBytes() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
    val sample = Integer.highestOneBit((maxSide / 1000).coerceAtLeast(1))
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: error("Invalid image")
    val scale = minOf(1f, 800f / maxOf(bitmap.width, bitmap.height))
    return if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
}
