package com.qedron.gateway.ui.main

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.qedron.gateway.Contact
import com.qedron.gateway.ContactBottomSheet
import com.qedron.gateway.ContactRecyclerViewAdapter
import com.qedron.gateway.ContactWithMessages
import com.qedron.gateway.ContactsActivity
import com.qedron.gateway.ContactsViewModelFactory
import com.qedron.gateway.EndlessRecyclerViewScrollListener
import com.qedron.gateway.R
import com.qedron.gateway.afterTextChanged
import com.qedron.gateway.databinding.FragmentContactsBinding


class ContactsFragment : Fragment() {

    companion object {
        fun newInstance() = ContactsFragment()
        const val CONTACT_ID = "contacts.id" // 'name' 'details' 'phoneNumber' 'tag' 'ranking' 'lastContact' 'messageSize'
        const val NAME = "name"
        const val DETAILS = "details"
        const val PHONE = "phoneNumber"
        const val TAG = "tag"
        const val RANKING = "ranking"
        const val LAST_CONTACT = "lastContact"
        const val BLOCK = "blocked"
        const val MESSAGES = "messageSize"

        const val ASC = "ASC"
        const val DESC = "DESC"
    }

    private var _binding:FragmentContactsBinding? = null
    private val binding get() = _binding!!
    private var initialPerPage = 10
    private var perPage = 10
    private var searchText = ""
    private var orderBy = CONTACT_ID // 'name' 'details' 'phoneNumber' 'tag' 'ranking' 'lastContact' 'messageSize'

    private var sortBy = ASC
    private lateinit var contactsAdapter: ContactRecyclerViewAdapter
    private val viewModel: ContactsViewModel by viewModels {
        context?.let { ContactsViewModelFactory(it) }!!
    }
    interface ContactClickListener {
        fun onBlockButtonClicked(contact: ContactWithMessages)

        fun onContactClicked(contact: ContactWithMessages)
    }



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_contact_list, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_order -> {
                        showSortMenu(menuItem)
                        true
                    }
                    R.id.action_sort -> {
                        // Handle menu item 2 click
                        if(sortBy == ASC) {
                            sortBy = DESC
                            menuItem.icon = AppCompatResources.getDrawable(context!!,R.drawable.ic_arrow_down_24)
                        }
                        else {
                            sortBy = ASC
                            menuItem.icon = AppCompatResources.getDrawable(context!!,R.drawable.ic_arrow_up_24)
                        }
                        reloadData()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        initContactsList()
    }

    private fun showSortMenu(menuItem: MenuItem) {
        val view = requireActivity().findViewById<View>(menuItem.itemId)
        val popupMenu = PopupMenu(ContextThemeWrapper(requireContext(), R.style.FlatTheme_PopupMenu) , view)
        popupMenu.menuInflater.inflate(R.menu.contacts_sort_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sort_by_tag -> {
                    orderBy = TAG
                    reloadData()
                    true
                }
                R.id.sort_by_name -> {
                    orderBy = NAME
                    reloadData()
                    true
                }
                R.id.sort_by_status -> {
                    orderBy = BLOCK
                    reloadData()
                    true
                }
                R.id.sort_by_rank -> {
                    orderBy = RANKING
                    reloadData()
                    true
                }
                R.id.sort_by_details -> {
                    orderBy = DETAILS
                    reloadData()
                    true
                }
                R.id.sort_by_phone -> {
                    orderBy = PHONE
                    reloadData()
                    true
                }
                R.id.sort_by_last_contact -> {
                    orderBy = LAST_CONTACT
                    reloadData()
                    true
                }
                R.id.sort_by_messages -> {
                    orderBy = MESSAGES
                    reloadData()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun reloadData(){
        contactsAdapter.clearData()
        viewModel.searchPaginatedContacts(searchText, 0, initialPerPage, orderBy, sortBy)
    }

    private fun initContactsList(){
        binding.contactList.apply {
            val linearLayoutManager = LinearLayoutManager(activity)
            layoutManager = linearLayoutManager
            itemAnimator = NoBlinkItemAnimator()
            contactsAdapter = ContactRecyclerViewAdapter(context, object:ContactClickListener{
                override fun onBlockButtonClicked(contact: ContactWithMessages) {
                    if(contact.contact.isTest) Toast.makeText(context,"Contact is a testing account ", Toast.LENGTH_LONG).show()
                    else contact.contact.apply { blocked = !blocked }
                    viewModel.updateContact(contact.contact)
                }
                override fun onContactClicked(contact: ContactWithMessages) {
                    ContactBottomSheet(context, contact, object :ContactBottomSheet.ContactDataChangedListener{
                        override fun onDataChanged(contact: Contact?) {
                            if (contact != null) {
                                contactsAdapter.dataChanged(contact)
                            }
                        }
                    }, viewModel).show()
                }
            })
            adapter = contactsAdapter
            setItemViewCacheSize(10)
            addOnScrollListener(object : EndlessRecyclerViewScrollListener(linearLayoutManager) {
                override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {
                    viewModel.searchPaginatedContacts(searchText, contactsAdapter.itemCount, perPage, orderBy, sortBy)
                }
            })
        }
        viewModel.searchPaginatedContacts(searchText, 0, initialPerPage, orderBy, sortBy)
        viewModel.contactList.observe(viewLifecycleOwner) { it ->
            contactsAdapter.appendData(it.toMutableList())
            if (contactsAdapter.itemCount > 0) {
                binding.emptyList.visibility = View.GONE
                binding.contactList.visibility = View.VISIBLE
                binding.searchContactInput.visibility = View.VISIBLE
            } else {
                binding.emptyList.visibility = View.VISIBLE
                binding.contactList.visibility = View.GONE
                binding.searchContactInput.visibility = View.GONE
            }
        }
        viewModel.updateContact.observe(viewLifecycleOwner) {
            if (it != null) {
                contactsAdapter.dataChanged(it)
                if (!it.isTest)
                    Toast.makeText(
                        context,
                        "contact is ${if (it.blocked) "blocked" else "active"}",
                        Toast.LENGTH_LONG
                    ).show()
            }
        }
        binding.searchContactInput.editText?.afterTextChanged {
            searchText = it
            contactsAdapter.clearData()
            viewModel.getContactsCount(searchText)
            viewModel.searchPaginatedContacts(searchText, 0, initialPerPage, orderBy, sortBy)
        }
        viewModel.getContactsCount("")
        viewModel.contactCount.observe(viewLifecycleOwner) { it ->
            (activity as ContactsActivity).supportActionBar?.subtitle = "$it contacts"
        }

        if(contactsAdapter.itemCount > 0){
            binding.emptyList.visibility = View.GONE
            binding.contactList.visibility = View.VISIBLE
            binding.searchContactInput.visibility = View.VISIBLE
        } else {
            binding.emptyList.visibility = View.VISIBLE
            binding.contactList.visibility = View.GONE
            binding.searchContactInput.visibility = View.GONE
        }
    }

    inner class NoBlinkItemAnimator : DefaultItemAnimator() {
        override fun animateChange(
            oldHolder: RecyclerView.ViewHolder?,
            newHolder: RecyclerView.ViewHolder?,
            fromX: Int, fromY: Int, toX: Int, toY: Int
        ): Boolean {
            // Disable change animations
            if (oldHolder != null) {
                dispatchChangeFinished(oldHolder, true)
            }
            if (newHolder != null) {
                dispatchChangeFinished(newHolder, false)
            }
            return false
        }
    }


}