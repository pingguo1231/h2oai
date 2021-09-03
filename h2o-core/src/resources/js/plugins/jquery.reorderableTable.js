// Generated by CoffeeScript 1.5.0
(function() {

  $.widget("swing.reorderabletable", {
    eventWidgetPrefix: 'dragtable',
    options: {
      draggedElementClass: 'dragged-header-table',
      newColumnSpotPlaceholderClass: 'dragged-header-new-column-spot-placeholder',
      dataHeader: 'data-header',
      appendTargetSelector: "body",
      headerSelector: "th:not(.no-drag)",
      scroll: true,
      stop: function() {}
    },
    _create: function() {
      this._mouseDownHandler = this._mouseDownHandlerFactory(this);
      this._mouseMoveHandler = this._mouseMoveHandlerFactory(this);
      this._mouseUpHandler = this._mouseUpHandlerFactory(this);
      return this.element.on('mousedown', this.options.headerSelector, this._mouseDownHandlerFactory(this));
    },
    _destroy: function() {
      this.destroyDraggedElement();
      return this.element.off('mousedown', this.options.headerSelector);
    },
    $columnHeaders: function() {
      return $(this.element.find(this.options.headerSelector));
    },
    recreateColumnSpotsCache: function() {
      return this.columnPositions = this.$columnHeaders().map(function(col) {
        return $(this).offset().left;
      });
    },
    columnIndexUnderDraggedElement: function() {
      var draggedElementPosX, idx, leftPos, _i, _len, _ref;
      draggedElementPosX = this.$draggedElement.offset().left + 0.5 * this.$draggedElement.outerWidth();
      idx = -1;
      _ref = this.columnPositions;
      for (_i = 0, _len = _ref.length; _i < _len; _i++) {
        leftPos = _ref[_i];
        if (leftPos > draggedElementPosX) {
          return idx;
        }
        idx += 1;
      }
      return idx;
    },
    createDraggedElementFrom: function($th, e) {
      this.destroyDraggedElement();
      this.recreateColumnSpotsCache();
      this.$draggedElement = $("<table></table>");
      this.$draggedElementTR = $("<tr></tr>");
      this.$draggedElementTH = $th.clone();
      this.$draggedElementTH.css({
        width: $th.width() - 1,
        height: $th.height() - 1
      });
      this.$draggedElement.append(this.$draggedElementTR);
      this.$draggedElementTR.append(this.$draggedElementTH);
      this.$draggedElement.attr('class', this.element.attr('class'));
      if (this.options.draggedElementClass != null) {
        this.$draggedElement.addClass(this.options.draggedElementClass);
      }
      this.startDragFrom($th, e);
      this.updateCursorPositionFromEvent(e);
      this.updateDraggedElementPosition();
      $(this.options.appendTargetSelector).append(this.$draggedElement);
      $(document.body).bind('mouseup', this._mouseUpHandler);
      return $(document.body).bind("mousemove", this._mouseMoveHandler);
    },
    destroyDraggedElement: function() {
      this.dehighlightPlaceOfInsertion();
      if (!this.$draggedElement) {
        return;
      }
      this.$draggedElement.detach();
      $(document.body).unbind("mousemove", this._mouseMoveHandler);
      return $(document.body).unbind('mouseup');
    },
    highlightPlaceOfInsertion: function() {
      var $columnUnderCursorElement;
      if (this.$highlightElement == null) {
        this.$highlightElement = $("<div></div>");
        this.$highlightElement.addClass(this.options.newColumnSpotPlaceholderClass);
        this.$highlightElement.css({
          position: "absolute"
        });
        $(this.options.appendTargetSelector).append(this.$highlightElement);
      }
      $columnUnderCursorElement = $(this.$columnHeaders()[this.currentlyOverIndex]);
      this.$highlightElement.css({
        left: this.columnPositions[this.currentlyOverIndex],
        top: this.element.offset().top,
        width: $columnUnderCursorElement.outerWidth(),
        height: this.element.height()
      });
    },
    dehighlightPlaceOfInsertion: function() {
      if (!this.$highlightElement) {
        return;
      }
      this.$highlightElement.detach();
      return this.$highlightElement = null;
    },
    startDragFrom: function($th, e) {
      this.origin = $th.offset();
      return this.originCursorX = e.pageX;
    },
    updateCursorPositionFromEvent: function(e) {
      return this.currentCursorX = e.pageX;
    },
    updateDraggedElementPosition: function() {
      return this.$draggedElement.css({
        position: "absolute",
        left: this.origin.left + (this.currentCursorX - this.originCursorX) - 1,
        top: this.origin.top
      });
    },
    order: function() {
      var columnIDAttr, order, swapItemIdx, withItemIdx;
      columnIDAttr = this.options.dataHeader;
      order = $.map(this.$columnHeaders(), function(item, idx) {
        return $(item).attr(columnIDAttr);
      });
      if (this.originalIndex !== this.currentlyOverIndex) {
        swapItemIdx = this.originalIndex;
        withItemIdx = this.currentlyOverIndex < 0 ? 0 : this.currentlyOverIndex;
        order.move(swapItemIdx, withItemIdx);
      }
      return order;
    },
    _mouseDownHandlerFactory: function(self) {
      return function(e) {
        self.createDraggedElementFrom($(this), e);
        self.originalIndex = self.$columnHeaders().index(this);
        self.currentlyOverIndex = self.originalIndex;
        return false;
      };
    },
    _mouseUpHandlerFactory: function(self) {
      return function(e) {
        self.destroyDraggedElement();
        return self.options.stop(self.order());
      };
    },
    _mouseMoveHandlerFactory: function(self) {
      return function(e) {
        self.updateCursorPositionFromEvent(e);
        self.updateDraggedElementPosition();
        self.currentlyOverIndex = self.columnIndexUnderDraggedElement();
        return self.highlightPlaceOfInsertion();
      };
    }
  });

}).call(this);
